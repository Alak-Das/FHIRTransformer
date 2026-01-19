package com.al.fhirhl7transformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.config.TenantContext;
import com.al.fhirhl7transformer.service.converter.*;
import com.al.fhirhl7transformer.util.DateTimeUtil;

import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.DiagnosticReport;

import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class Hl7ToFhirService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Hl7ToFhirService.class);
    private final HapiContext hl7Context;
    private final FhirContext fhirContext;
    private final FhirValidationService fhirValidationService;
    private final MeterRegistry meterRegistry;
    private final PatientConverter patientConverter;
    private final EncounterConverter encounterConverter;
    private final ObservationConverter observationConverter;
    private final AllergyConverter allergyConverter;
    private final ConditionConverter conditionConverter;
    private final MedicationConverter medicationConverter;
    private final ProcedureConverter procedureConverter;
    private final InsuranceConverter insuranceConverter;
    private final AppointmentConverter appointmentConverter;
    private final ImmunizationConverter immunizationConverter;
    private final ServiceRequestConverter serviceRequestConverter;
    private final DiagnosticReportConverter diagnosticReportConverter;
    private final MedicationAdministrationConverter medicationAdministrationConverter;
    private final PractitionerConverter practitionerConverter;

    @Autowired
    public Hl7ToFhirService(FhirValidationService fhirValidationService, FhirContext fhirContext,
            HapiContext hapiContext, MeterRegistry meterRegistry,
            PatientConverter patientConverter, EncounterConverter encounterConverter,
            ObservationConverter observationConverter, AllergyConverter allergyConverter,
            ConditionConverter conditionConverter, MedicationConverter medicationConverter,
            ProcedureConverter procedureConverter, InsuranceConverter insuranceConverter,
            AppointmentConverter appointmentConverter, ImmunizationConverter immunizationConverter,
            ServiceRequestConverter serviceRequestConverter, DiagnosticReportConverter diagnosticReportConverter,
            MedicationAdministrationConverter medicationAdministrationConverter,
            PractitionerConverter practitionerConverter) {
        this.hl7Context = hapiContext;
        this.fhirContext = fhirContext;
        this.fhirValidationService = fhirValidationService;
        this.meterRegistry = meterRegistry;
        this.patientConverter = patientConverter;
        this.encounterConverter = encounterConverter;
        this.observationConverter = observationConverter;
        this.allergyConverter = allergyConverter;
        this.conditionConverter = conditionConverter;
        this.medicationConverter = medicationConverter;
        this.procedureConverter = procedureConverter;
        this.insuranceConverter = insuranceConverter;
        this.appointmentConverter = appointmentConverter;
        this.immunizationConverter = immunizationConverter;
        this.serviceRequestConverter = serviceRequestConverter;
        this.diagnosticReportConverter = diagnosticReportConverter;
        this.medicationAdministrationConverter = medicationAdministrationConverter;
        this.practitionerConverter = practitionerConverter;
    }

    public String convertHl7ToFhir(String hl7Message) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // Parse HL7 Message
            Parser p = hl7Context.getPipeParser();
            Message hapiMsg = p.parse(hl7Message);
            Terser terser = new Terser(hapiMsg);

            // Create FHIR Bundle
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.TRANSACTION);

            // Use MSH-10 as Bundle Transaction ID (Preserve Integrity)
            String msh10 = terser.get("/.MSH-10");
            if (msh10 != null && !msh10.isEmpty()) {
                bundle.setId(msh10);
            } else {
                bundle.setId(UUID.randomUUID().toString());
            }

            // Add Tenant ID to Bundle Meta
            String tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                Meta meta = new Meta();
                meta.addTag("http://example.org/tenant-id", tenantId, "Tenant ID");
                bundle.setMeta(meta);
            }

            log.info("Starting HL7 to FHIR conversion for transaction: {}", msh10);

            // Extract Trigger Event (MSH-9-2)
            String triggerEvent = terser.get("/.MSH-9-2");

            String patientId = UUID.randomUUID().toString();
            ConversionContext context = ConversionContext.builder()
                    .patientId(patientId)
                    .hapiMessage(hapiMsg)
                    .triggerEvent(triggerEvent)
                    .build();

            // Extract Patient Data
            List<Patient> patients = patientConverter.convert(terser, bundle, context);
            Patient patient = null;
            if (!patients.isEmpty()) {
                patient = patients.get(0);
                bundle.addEntry().setResource(patient).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");
            } else {
                log.error("Patient conversion failed to return a resource");
            }

            // Map Encounter Data
            List<Encounter> encounters = encounterConverter.convert(terser, bundle, context);
            for (Encounter enc : encounters) {
                bundle.addEntry().setResource(enc).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Encounter");
            }

            // Map OBX segments (Observations)
            List<Observation> observations = observationConverter.convert(terser, bundle, context);
            for (Observation obs : observations) {
                bundle.addEntry().setResource(obs).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Observation");
            }

            // Map DG1 segments (Conditions)
            // Map Conditions (DG1)
            List<Condition> conditions = conditionConverter.convert(terser, bundle, context);
            for (Condition cond : conditions) {
                bundle.addEntry().setResource(cond).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Condition");
            }

            // Map AL1 segments (Allergies)
            List<AllergyIntolerance> allergies = allergyConverter.convert(terser, bundle, context);
            for (AllergyIntolerance allergy : allergies) {
                bundle.addEntry().setResource(allergy).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("AllergyIntolerance");
            }

            // Map Medication segments (RXE, RXO, RXA)
            List<MedicationRequest> medications = medicationConverter.convert(terser, bundle, context);
            for (MedicationRequest med : medications) {
                bundle.addEntry().setResource(med).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("MedicationRequest");
            }

            // Map MedicationAdministration segments (RXA)
            List<MedicationAdministration> adminList = medicationAdministrationConverter.convert(terser, bundle,
                    context);
            for (MedicationAdministration admin : adminList) {
                bundle.addEntry().setResource(admin).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("MedicationAdministration");
            }

            // Map Practitioners (PV1/ORC)
            List<Practitioner> practitioners = practitionerConverter.convert(terser, bundle, context);
            for (Practitioner practitioner : practitioners) {
                bundle.addEntry().setResource(practitioner).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Practitioner");
            }

            // Map Procedures (PR1)
            List<Procedure> procedures = procedureConverter.convert(terser, bundle, context);
            for (Procedure proc : procedures) {
                bundle.addEntry().setResource(proc).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Procedure");
            }

            // Map ServiceRequests (OBR) - Run BEFORE DiagnosticReports for linking
            List<ServiceRequest> serviceRequests = serviceRequestConverter.convert(terser, bundle, context);
            for (ServiceRequest sr : serviceRequests) {
                bundle.addEntry().setResource(sr).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("ServiceRequest");
            }

            // Map DiagnosticReports (OBR) - Links to ServiceRequests
            List<DiagnosticReport> reports = diagnosticReportConverter.convert(terser, bundle, context);
            for (DiagnosticReport rep : reports) {
                bundle.addEntry().setResource(rep).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("DiagnosticReport");
            }

            // Map Immunizations (RXA)
            List<Immunization> immunizations = immunizationConverter.convert(terser, bundle, context);
            for (Immunization imm : immunizations) {
                bundle.addEntry().setResource(imm).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Immunization");
            }

            // Map Appointments (SCH)
            List<Appointment> appointments = appointmentConverter.convert(terser, bundle, context);
            for (Appointment app : appointments) {
                bundle.addEntry().setResource(app).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Appointment");
            }

            // Map IN1/GT1 segments (Insurance/Guarantor)
            List<DomainResource> insuranceResources = insuranceConverter.convert(terser, bundle, context);
            for (DomainResource res : insuranceResources) {
                if (res instanceof Coverage) {
                    bundle.addEntry().setResource(res).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Coverage");
                } else if (res instanceof RelatedPerson) {
                    bundle.addEntry().setResource(res).getRequest().setMethod(Bundle.HTTPVerb.POST)
                            .setUrl("RelatedPerson");
                } else if (res instanceof Organization) {
                    bundle.addEntry().setResource(res).getRequest().setMethod(Bundle.HTTPVerb.POST)
                            .setUrl("Organization");
                }
            }

            // Create Provenance Resource for Auditability
            Provenance provenance = new Provenance();
            provenance.setId(UUID.randomUUID().toString());

            // recorded (MSH-7)
            String msh7 = terser.get("/.MSH-7");
            if (msh7 != null && !msh7.isEmpty()) {
                try {
                    DateTimeType dt = DateTimeUtil.hl7DateTimeToFhir(msh7);
                    if (dt != null)
                        provenance.setRecorded(dt.getValue());
                } catch (Exception e) {
                    log.debug("Failed to parse MSH-7 for Provenance: {}", msh7);
                }
            } else {
                provenance.setRecorded(new Date());
            }

            // agent (Sending App/Facility)
            Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
            agent.getType().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType")
                    .setCode("AUT").setDisplay("Author");

            String sendingApp = terser.get("/.MSH-3");
            String sendingFacility = terser.get("/.MSH-4");
            Reference agentWho = new Reference();
            if (sendingApp != null || sendingFacility != null) {
                StringBuilder sb = new StringBuilder();
                if (sendingApp != null)
                    sb.append(sendingApp);
                if (sendingFacility != null) {
                    if (sb.length() > 0)
                        sb.append(" at ");
                    sb.append(sendingFacility);
                }
                agentWho.setDisplay(sb.toString());
            } else {
                agentWho.setDisplay("FHIR Transformer");
            }
            agent.setWho(agentWho);

            // target (All resources in the bundle)
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource() != null) {
                    provenance.addTarget(new Reference(
                            entry.getResource().getResourceType().name() + "/" + entry.getResource().getId()));
                }
            }

            bundle.addEntry().setResource(provenance).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Provenance");

            log.info("Conversion complete. Bundle contains {} entries.", bundle.getEntry().size());

            // Validate the Bundle
            // Validate the Bundle
            fhirValidationService.validateAndThrow(bundle);

            // Serialize to JSON
            String result = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);

            // Record Success Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "v2-to-fhir", "status", "success").increment();
            sample.stop(meterRegistry.timer("fhir.conversion.time", "type", "v2-to-fhir"));

            log.debug("Converted HL7 to FHIR Bundle. Original structure: {}. Result length: {}",
                    hapiMsg.getName(), result.length());

            return result;

        } catch (Exception e) {
            log.error("Error converting HL7 to FHIR: {}", e.getMessage(), e);
            // Record Failure Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "v2-to-fhir", "status", "error").increment();
            throw e;
        }
    }

}
