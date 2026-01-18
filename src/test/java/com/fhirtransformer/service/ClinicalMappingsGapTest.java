package com.fhirtransformer.service;

import com.fhirtransformer.service.converter.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import com.fhirtransformer.service.converter.ConditionConverter;
import com.fhirtransformer.service.converter.MedicationConverter;
import com.fhirtransformer.service.converter.ProcedureConverter;
import com.fhirtransformer.service.converter.InsuranceConverter;
import ca.uhn.fhir.validation.ValidationResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClinicalMappingsGapTest {

    private Hl7ToFhirService hl7ToFhirService;

    @BeforeEach
    public void setUp() {
        HapiContext hapiContext = new DefaultHapiContext();
        FhirContext fhirContext = FhirContext.forR4();
        FhirValidationService fhirValidationService = Mockito.mock(FhirValidationService.class);
        ValidationResult validationResult = mock(ValidationResult.class);
        when(validationResult.isSuccessful()).thenReturn(true);
        when(fhirValidationService.validate(any())).thenReturn(validationResult);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        PatientConverter patientConverter = new PatientConverter();
        EncounterConverter encounterConverter = new EncounterConverter();
        ObservationConverter observationConverter = new ObservationConverter();
        AllergyConverter allergyConverter = new AllergyConverter();

        ConditionConverter conditionConverter = new ConditionConverter();
        MedicationConverter medicationConverter = new MedicationConverter();
        ProcedureConverter procedureConverter = new ProcedureConverter();
        InsuranceConverter insuranceConverter = new InsuranceConverter();
        AppointmentConverter appointmentConverter = new AppointmentConverter();
        ImmunizationConverter immunizationConverter = new ImmunizationConverter();
        ServiceRequestConverter serviceRequestConverter = new ServiceRequestConverter();
        DiagnosticReportConverter diagnosticReportConverter = new DiagnosticReportConverter();
        MedicationAdministrationConverter medicationAdministrationConverter = new MedicationAdministrationConverter();
        PractitionerConverter practitionerConverter = new PractitionerConverter();
        hl7ToFhirService = new Hl7ToFhirService(fhirValidationService, fhirContext, hapiContext, meterRegistry,
                patientConverter, encounterConverter, observationConverter, allergyConverter,
                conditionConverter, medicationConverter, procedureConverter, insuranceConverter,
                appointmentConverter, immunizationConverter, serviceRequestConverter, diagnosticReportConverter,
                medicationAdministrationConverter, practitionerConverter);
    }

    @Test
    public void testImmunizationConversion() throws Exception {
        // Use ADT^A01 for simplified structure testing
        String hl7 = "MSH|^~\\&|IMM|HOSP|REG|OFFICE|202301011200||ADT^A01|123456|P|2.5\r" +
                "PID|1||104||DOE^JOHN||19900101|M\r" +
                "RXA|0|1|202301011200|202301011200|998^Infectious Disease Code^CVX|0.5|mL^milliliters^UCUM||00^Administered^NIP||||||LOT123|20240101|MSD^Merck^MVX|||CP";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
        System.out.println("Immunization FHIR: " + fhir);

        assertNotNull(fhir);
        assertTrue(fhir.contains("Immunization"), "Should contain Immunization resource");
        assertTrue(fhir.contains("998"), "Should contain Vaccine Code");
        assertTrue(fhir.contains("Infectious Disease Code"), "Should contain Vaccine Display");
        assertTrue(fhir.contains("LOT123"), "Should contain Lot Number");
        assertTrue(fhir.contains("MSD") || fhir.contains("Merck"), "Should contain Manufacturer ID or Name");
        assertTrue(fhir.contains("Organization"), "Should contain Organization resource");
        assertTrue(fhir.contains("completed"), "Should contain Status completed");
        // Add Performer and check Physician
        // RXA-9 is 00^Administered^NIP. RXA-10 is the next field after the pipe.
        hl7 = hl7.replace("00^Administered^NIP||||||LOT123", "00^Administered^NIP|DOC123^Dr. Smith^L|||||LOT123");
        fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
        assertTrue(fhir.contains("Practitioner"), "Should contain Practitioner resource");
        assertTrue(fhir.contains("Dr. Smith"), "Should contain Practitioner Name");
    }

    @Test
    public void testAppointmentConversion() throws Exception {
        // Use ADT^A01 for simplified structure testing
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|202301011200||ADT^A01|MSG001|P|2.5\r" +
                "PID|1||104||APP^TEST||19900101|M\r" +
                "SCH|PLACER123|FILLER456||||RECHECK^Recheck Visit|||30|m|^^^202305011000\r";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
        System.out.println("Appointment FHIR: " + fhir);

        assertNotNull(fhir);
        assertTrue(fhir.contains("Appointment"), "Should contain Appointment resource");
        assertTrue(fhir.contains("FILLER456"), "Should contain Filler ID");
        assertTrue(fhir.contains("Recheck Visit"), "Should contain Reason");
        assertTrue(fhir.contains("2023-05-01"), "Should contain Start Date");
        assertTrue(fhir.contains("booked"), "Should contain Status booked");
    }

    @Test
    public void testServiceRequestConversion() throws Exception {
        // Use ADT^A01 for simplified structure testing
        // OBR-4: Chest X-Ray, OBR-5: S (Stat)
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|202301011200||ADT^A01|MSG001|P|2.5\r" +
                "PID|1||104||ORDER^TEST||19900101|M\r" +
                "OBR|1|ORD123||12345-6^Chest X-Ray^LN|S||202301011200";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);
        System.out.println("ServiceRequest FHIR: " + fhir);

        assertNotNull(fhir);
        assertTrue(fhir.contains("ServiceRequest"), "Should contain ServiceRequest resource");
        assertTrue(fhir.contains("12345-6"), "Should contain Service Code");
        assertTrue(fhir.contains("Chest X-Ray"), "Should contain Service Display");
        assertTrue(fhir.contains("order"), "Should contain Intent order");
        assertTrue(fhir.contains("active"), "Should contain Status active");
        assertTrue(fhir.contains("stat"), "Should contain Priority stat");
    }
}
