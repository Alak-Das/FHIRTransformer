package com.al.fhirhl7transformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import com.al.fhirhl7transformer.config.ParsingConfiguration;
import com.al.fhirhl7transformer.service.converter.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FeatureVerificationTest {

    private FhirValidationService fhirValidationService;
    private Hl7ToFhirService hl7ToFhirService;

    private MeterRegistry meterRegistry;

    @Mock
    private PatientConverter patientConverter;
    @Mock
    private EncounterConverter encounterConverter;
    @Mock
    private ObservationConverter observationConverter;
    @Mock
    private AllergyConverter allergyConverter;
    @Mock
    private ConditionConverter conditionConverter;
    @Mock
    private MedicationConverter medicationConverter;
    @Mock
    private ProcedureConverter procedureConverter;
    @Mock
    private InsuranceConverter insuranceConverter;
    @Mock
    private AppointmentConverter appointmentConverter;
    @Mock
    private ImmunizationConverter immunizationConverter;
    @Mock
    private ServiceRequestConverter serviceRequestConverter;
    @Mock
    private DiagnosticReportConverter diagnosticReportConverter;
    @Mock
    private MedicationAdministrationConverter medicationAdministrationConverter;
    @Mock
    private PractitionerConverter practitionerConverter;
    @Mock
    private LocationConverter locationConverter;
    @Mock
    private OrganizationConverter organizationConverter;
    @Mock
    private SpecimenConverter specimenConverter;
    @Mock
    private CommunicationConverter communicationConverter;
    @Mock
    private DeviceConverter deviceConverter;
    @Mock
    private OrderConverter orderConverter;
    @Mock
    private DocumentReferenceConverter documentReferenceConverter;
    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private CarePlanConverter carePlanConverter;
    @Mock
    private PractitionerRoleConverter practitionerRoleConverter;
    @Mock
    private MessageHeaderConverter messageHeaderConverter;

    @Mock
    private Hl7ConverterRegistry converterRegistry;

    private ParsingConfiguration parsingConfiguration;
    private HapiContext hapiContext;
    private FhirContext fhirContext;

    @BeforeEach
    void setUp() {
        fhirValidationService = new FhirValidationService();
        hapiContext = new DefaultHapiContext();
        fhirContext = FhirContext.forR4();
        meterRegistry = new SimpleMeterRegistry();
        parsingConfiguration = new ParsingConfiguration();
        parsingConfiguration.setSupportedVersions(Arrays.asList("2.3", "2.5", "2.5.1", "2.6"));

        setupRegistryMocks();

        hl7ToFhirService = new Hl7ToFhirService(
                fhirValidationService, fhirContext, hapiContext, meterRegistry,
                converterRegistry, parsingConfiguration, subscriptionService);
    }

    @Test
    void testFhirValidation() {
        Patient p = new Patient();
        p.addName().setFamily("Doe").addGiven("John");
        p.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE);
        p.addIdentifier().setSystem("urn:system").setValue("12345");

        ca.uhn.fhir.validation.ValidationResult result = fhirValidationService.validate(p);
        assertTrue(result.isSuccessful(), "Validation should succeed for a basic valid patient");
    }

    @Test
    void testHl7VersionNegotiation_Supported() throws Exception {
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1001|P|2.5\rPID|1||100||DOE^JOHN";

        parsingConfiguration.setValidationEnabled(false);
        setupDefaultMocks();

        String result = hl7ToFhirService.convertHl7ToFhir(hl7);
        assertNotNull(result);
        assertFalse(result.contains("Unsupported HL7 version"), "Should not have version warning");
    }

    @Test
    void testHl7VersionNegotiation_UnsupportedStrict() {
        parsingConfiguration.setStrictness(ParsingConfiguration.StrictnessLevel.STRICT);
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1001|P|2.1\rPID|1||100||DOE^JOHN";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            hl7ToFhirService.convertHl7ToFhir(hl7);
        });

        assertTrue(exception.getMessage().contains("Unsupported HL7 version: 2.1"));
    }

    @Test
    void testHl7VersionNegotiation_UnsupportedLenient() throws Exception {
        parsingConfiguration.setStrictness(ParsingConfiguration.StrictnessLevel.LENIENT);
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1001|P|2.1\rPID|1||100||DOE^JOHN";

        parsingConfiguration.setValidationEnabled(false);
        setupDefaultMocks();

        String result = hl7ToFhirService.convertHl7ToFhir(hl7);
        assertTrue(result.contains("Unsupported HL7 version: 2.1"), "Should contain version warning in results");
        assertTrue(result.contains("OperationOutcome"), "Should contain OperationOutcome with warning");
    }

    @Test
    void testFhirValidation_TerminologyFailure() {
        Patient p = new Patient();
        p.addName().setFamily("Doe");
        // Use CodeableConcept to avoid immediate Enum exception, letting the validator
        // handle it
        p.getMaritalStatus().addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus")
                .setCode("INVALID_CODE");

        ca.uhn.fhir.validation.ValidationResult result = fhirValidationService.validate(p);
        // Should contain a message about the invalid code
        assertTrue(result.getMessages().stream()
                .anyMatch(m -> m.getMessage().contains("Unknown code") || m.getMessage().contains("not in value set")),
                "Validation should report invalid code");
    }

    @Test
    void testHl7VersionNegotiation_Configurable() throws Exception {
        parsingConfiguration.setStrictness(ParsingConfiguration.StrictnessLevel.STRICT);
        parsingConfiguration.setValidationEnabled(false);
        // Explicitly support 2.1 which was previously unsupported
        parsingConfiguration.setSupportedVersions(Arrays.asList("2.1", "2.5"));
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1001|P|2.1\rPID|1||100||DOE^JOHN";

        setupDefaultMocks();

        String result = hl7ToFhirService.convertHl7ToFhir(hl7);
        assertNotNull(result, "Conversion should succeed when version is added to supported list");
        assertFalse(result.contains("Unsupported HL7 version"), "2.1 should now be supported");
    }

    private void setupDefaultMocks() throws Exception {
        when(organizationConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(locationConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(patientConverter.convert(any(), any(), any())).thenReturn(Collections.singletonList(new Patient()));
        when(encounterConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(observationConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(conditionConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(allergyConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(medicationConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(medicationAdministrationConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(practitionerConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(immunizationConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(serviceRequestConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(diagnosticReportConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(insuranceConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(appointmentConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(procedureConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(specimenConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(communicationConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(deviceConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(orderConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(orderConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(documentReferenceConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(carePlanConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(practitionerRoleConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
        when(messageHeaderConverter.convert(any(), any(), any())).thenReturn(Collections.emptyList());
    }

    private void setupRegistryMocks() {
        when(converterRegistry.getPatientConverter()).thenReturn(patientConverter);
        when(converterRegistry.getEncounterConverter()).thenReturn(encounterConverter);
        when(converterRegistry.getObservationConverter()).thenReturn(observationConverter);
        when(converterRegistry.getAllergyConverter()).thenReturn(allergyConverter);
        when(converterRegistry.getConditionConverter()).thenReturn(conditionConverter);
        when(converterRegistry.getMedicationConverter()).thenReturn(medicationConverter);
        when(converterRegistry.getProcedureConverter()).thenReturn(procedureConverter);
        when(converterRegistry.getInsuranceConverter()).thenReturn(insuranceConverter);
        when(converterRegistry.getAppointmentConverter()).thenReturn(appointmentConverter);
        when(converterRegistry.getImmunizationConverter()).thenReturn(immunizationConverter);
        when(converterRegistry.getServiceRequestConverter()).thenReturn(serviceRequestConverter);
        when(converterRegistry.getDiagnosticReportConverter()).thenReturn(diagnosticReportConverter);
        when(converterRegistry.getMedicationAdministrationConverter()).thenReturn(medicationAdministrationConverter);
        when(converterRegistry.getPractitionerConverter()).thenReturn(practitionerConverter);
        when(converterRegistry.getLocationConverter()).thenReturn(locationConverter);
        when(converterRegistry.getOrganizationConverter()).thenReturn(organizationConverter);
        when(converterRegistry.getSpecimenConverter()).thenReturn(specimenConverter);
        when(converterRegistry.getCommunicationConverter()).thenReturn(communicationConverter);
        when(converterRegistry.getDeviceConverter()).thenReturn(deviceConverter);
        when(converterRegistry.getOrderConverter()).thenReturn(orderConverter);
        when(converterRegistry.getOrderConverter()).thenReturn(orderConverter);
        when(converterRegistry.getDocumentReferenceConverter()).thenReturn(documentReferenceConverter);
        when(converterRegistry.getCarePlanConverter()).thenReturn(carePlanConverter);
        when(converterRegistry.getPractitionerRoleConverter()).thenReturn(practitionerRoleConverter);
        when(converterRegistry.getMessageHeaderConverter()).thenReturn(messageHeaderConverter);
    }
}
