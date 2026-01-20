package com.al.fhirhl7transformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.config.TenantContext;
import com.al.fhirhl7transformer.service.converter.*;
import com.al.fhirhl7transformer.config.ParsingConfiguration;
import com.al.fhirhl7transformer.dto.ConversionError;
import com.al.fhirhl7transformer.util.OperationOutcomeBuilder;
import com.al.fhirhl7transformer.util.DateTimeUtil;

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
    private final Hl7ConverterRegistry converterRegistry;
    private final ParsingConfiguration parsingConfiguration;
    private final SubscriptionService subscriptionService;

    @Autowired
    public Hl7ToFhirService(
            FhirValidationService fhirValidationService,
            FhirContext fhirContext,
            HapiContext hapiContext,
            MeterRegistry meterRegistry,
            Hl7ConverterRegistry converterRegistry,
            ParsingConfiguration parsingConfiguration,
            SubscriptionService subscriptionService) {
        this.hl7Context = hapiContext;
        this.fhirContext = fhirContext;
        this.fhirValidationService = fhirValidationService;
        this.meterRegistry = meterRegistry;
        this.converterRegistry = converterRegistry;
        this.parsingConfiguration = parsingConfiguration;
        this.subscriptionService = subscriptionService;
    }

    public String convertHl7ToFhir(String hl7Message) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        List<ConversionError> errors = new ArrayList<>();

        try {
            // Parse HL7 Message
            Parser p = hl7Context.getPipeParser();
            Message hapiMsg;
            try {
                hapiMsg = p.parse(hl7Message);
            } catch (Exception e) {
                log.error("Failed to parse HL7 message", e);
                meterRegistry.counter("fhir.conversion.count", "type", "v2-to-fhir", "status", "error").increment();
                throw e;
            }
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
            log.info("Processing trigger event: {}", triggerEvent);

            // Version Negotiation (MSH-12)
            String versionId = terser.get("/.MSH-12");
            log.debug("HL7 Message Version: {}", versionId);

            if (versionId != null && !parsingConfiguration.getSupportedVersions().contains(versionId)) {
                String errorMsg = "Unsupported HL7 version: " + versionId + ". Supported versions: "
                        + parsingConfiguration.getSupportedVersions();
                if (parsingConfiguration.getStrictness() == ParsingConfiguration.StrictnessLevel.STRICT) {
                    log.error("Strict mode: {}", errorMsg);
                    throw new IllegalArgumentException(errorMsg);
                } else {
                    log.warn(errorMsg);
                    errors.add(ConversionError.warning("MSH", errorMsg));
                }
            }
            String msgType = terser.get("/.MSH-9-1");

            String patientId = UUID.randomUUID().toString();
            ConversionContext context = ConversionContext.builder()
                    .patientId(patientId)
                    .hapiMessage(hapiMsg)
                    .triggerEvent(triggerEvent)
                    .build();

            // Organizations
            try {
                List<Organization> organizations = converterRegistry.getOrganizationConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, organizations, "Organization");
            } catch (Exception e) {
                handleConverterError("Organization", e, errors);
            }

            // Locations
            try {
                List<Location> locations = converterRegistry.getLocationConverter().convert(terser, bundle, context);
                addToBundle(bundle, locations, "Location");
            } catch (Exception e) {
                handleConverterError("Location", e, errors);
            }

            // Extract Patient Data
            try {
                List<Patient> patients = converterRegistry.getPatientConverter().convert(terser, bundle, context);
                if (!patients.isEmpty()) {
                    addToBundle(bundle, patients, "Patient");
                } else {
                    log.error("Patient conversion failed to return a resource");
                    errors.add(ConversionError.builder().message("Patient conversion returned no resources")
                            .severity(ConversionError.Severity.ERROR).build());
                }
            } catch (Exception e) {
                handleConverterError("Patient", e, errors);
            }

            // Encounters
            try {
                List<Encounter> encounters = converterRegistry.getEncounterConverter().convert(terser, bundle, context);
                addToBundle(bundle, encounters, "Encounter");
            } catch (Exception e) {
                handleConverterError("Encounter", e, errors);
            }

            // Observations
            try {
                List<Observation> observations = converterRegistry.getObservationConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, observations, "Observation");
            } catch (Exception e) {
                handleConverterError("Observation", e, errors);
            }

            // Conditions
            try {
                List<Condition> conditions = converterRegistry.getConditionConverter().convert(terser, bundle, context);
                addToBundle(bundle, conditions, "Condition");
            } catch (Exception e) {
                handleConverterError("Condition", e, errors);
            }

            // Allergies
            try {
                List<AllergyIntolerance> allergies = converterRegistry.getAllergyConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, allergies, "AllergyIntolerance");
            } catch (Exception e) {
                handleConverterError("AllergyIntolerance", e, errors);
            }

            // Medications
            try {
                List<MedicationRequest> medications = converterRegistry.getMedicationConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, medications, "MedicationRequest");
            } catch (Exception e) {
                handleConverterError("MedicationRequest", e, errors);
            }

            // MedicationAdministration
            try {
                List<MedicationAdministration> adminList = converterRegistry.getMedicationAdministrationConverter()
                        .convert(terser, bundle,
                                context);
                addToBundle(bundle, adminList, "MedicationAdministration");
            } catch (Exception e) {
                handleConverterError("MedicationAdministration", e, errors);
            }

            // Practitioners
            try {
                List<Practitioner> practitioners = converterRegistry.getPractitionerConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, practitioners, "Practitioner");
            } catch (Exception e) {
                handleConverterError("Practitioner", e, errors);
            }

            // Procedures
            try {
                List<Procedure> procedures = converterRegistry.getProcedureConverter().convert(terser, bundle, context);
                addToBundle(bundle, procedures, "Procedure");
            } catch (Exception e) {
                handleConverterError("Procedure", e, errors);
            }

            // Specimen (New)
            try {
                List<Specimen> specimens = converterRegistry.getSpecimenConverter().convert(terser, bundle, context);
                addToBundle(bundle, specimens, "Specimen");
            } catch (Exception e) {
                handleConverterError("Specimen", e, errors);
            }

            // ServiceRequests / Orders
            // For ORM messages, use OrderConverter. For others, use existing
            // ServiceRequestConverter
            if ("ORM".equals(msgType)) {
                try {
                    List<DomainResource> resources = converterRegistry.getOrderConverter().convert(terser, bundle,
                            context);
                    for (DomainResource res : resources) {
                        bundle.addEntry().setResource(res).getRequest().setMethod(Bundle.HTTPVerb.POST)
                                .setUrl(res.getResourceType().name());
                    }
                } catch (Exception e) {
                    handleConverterError("Order", e, errors);
                }
            } else {
                try {
                    List<ServiceRequest> serviceRequests = converterRegistry.getServiceRequestConverter()
                            .convert(terser, bundle, context);
                    addToBundle(bundle, serviceRequests, "ServiceRequest");
                } catch (Exception e) {
                    handleConverterError("ServiceRequest", e, errors);
                }
            }

            // DiagnosticReports
            try {
                List<DiagnosticReport> reports = converterRegistry.getDiagnosticReportConverter().convert(terser,
                        bundle, context);
                addToBundle(bundle, reports, "DiagnosticReport");
            } catch (Exception e) {
                handleConverterError("DiagnosticReport", e, errors);
            }

            // Immunizations
            try {
                List<Immunization> immunizations = converterRegistry.getImmunizationConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, immunizations, "Immunization");
            } catch (Exception e) {
                handleConverterError("Immunization", e, errors);
            }

            // Appointments
            try {
                List<Appointment> appointments = converterRegistry.getAppointmentConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, appointments, "Appointment");
            } catch (Exception e) {
                handleConverterError("Appointment", e, errors);
            }

            // Communication (New)
            try {
                List<Communication> comms = converterRegistry.getCommunicationConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, comms, "Communication");
            } catch (Exception e) {
                handleConverterError("Communication", e, errors);
            }

            // Device (New)
            try {
                List<Device> devices = converterRegistry.getDeviceConverter().convert(terser, bundle, context);
                addToBundle(bundle, devices, "Device");
            } catch (Exception e) {
                handleConverterError("Device", e, errors);
            }

            // DocumentReference (New) - for MDM or others
            if ("MDM".equals(msgType) || "T02".equals(triggerEvent)) {
                try {
                    List<DocumentReference> docs = converterRegistry.getDocumentReferenceConverter().convert(terser,
                            bundle, context);
                    addToBundle(bundle, docs, "DocumentReference");
                } catch (Exception e) {
                    handleConverterError("DocumentReference", e, errors);
                }
            }

            // MessageHeader (New)
            try {
                List<MessageHeader> headers = converterRegistry.getMessageHeaderConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, headers, "MessageHeader");
            } catch (Exception e) {
                handleConverterError("MessageHeader", e, errors);
            }

            // CarePlan (New) - from ORC usually
            try {
                List<CarePlan> carePlans = converterRegistry.getCarePlanConverter().convert(terser, bundle, context);
                addToBundle(bundle, carePlans, "CarePlan");
            } catch (Exception e) {
                handleConverterError("CarePlan", e, errors);
            }

            // PractitionerRole (New) - from ROL
            try {
                List<PractitionerRole> roles = converterRegistry.getPractitionerRoleConverter().convert(terser, bundle,
                        context);
                addToBundle(bundle, roles, "PractitionerRole");
            } catch (Exception e) {
                handleConverterError("PractitionerRole", e, errors);
            }

            // Insurance / RelatedPerson / Organizations (IN1/GT1)
            try {
                List<DomainResource> insuranceResources = converterRegistry.getInsuranceConverter().convert(terser,
                        bundle, context);
                for (DomainResource res : insuranceResources) {
                    bundle.addEntry().setResource(res).getRequest().setMethod(Bundle.HTTPVerb.POST)
                            .setUrl(res.getResourceType().name());
                }
            } catch (Exception e) {
                handleConverterError("Insurance", e, errors);
            }

            // Create Provenance Resource
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

            // Add OperationOutcome if errors exist
            if (!errors.isEmpty()) {
                org.hl7.fhir.r4.model.OperationOutcome outcome = OperationOutcomeBuilder.fromErrors(errors);
                bundle.addEntry().setResource(outcome).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("OperationOutcome");
            }

            log.info("Conversion complete. Bundle contains {} entries.", bundle.getEntry().size());

            // Validate the Bundle
            if (parsingConfiguration.isValidationEnabled()) {
                try {
                    fhirValidationService.validateAndThrow(bundle);
                } catch (Exception e) {
                    if (parsingConfiguration.getStrictness() == ParsingConfiguration.StrictnessLevel.STRICT) {
                        meterRegistry.counter("fhir.conversion.count", "type", "v2-to-fhir", "status", "error")
                                .increment();
                        throw e;
                    } else {
                        log.warn("Validation failed but continuing (Flexible mode): {}", e.getMessage());
                    }
                }
            }

            // Check for Subscriptions and Notify
            // Using logic internal to checkAndNotify to handle null tenantId if needed
            subscriptionService.checkAndNotify(bundle, TenantContext.getTenantId());

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
            meterRegistry.counter("fhir.conversion.count", "type", "v2-to-fhir", "status", "error").increment();
            throw e;
        }
    }

    private <T extends Resource> void addToBundle(Bundle bundle, List<T> resources, String resourceType) {
        for (T res : resources) {
            bundle.addEntry().setResource(res).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl(resourceType);
        }
    }

    private void handleConverterError(String converterName, Exception e, List<ConversionError> errors)
            throws Exception {
        log.error("Error in {} converter: {}", converterName, e.getMessage());
        if (parsingConfiguration.shouldContinueOnError()) {
            errors.add(ConversionError.builder()
                    .message("Error in " + converterName + ": " + e.getMessage())
                    .severity(ConversionError.Severity.ERROR)
                    .build());
        } else {
            throw e;
        }

    }
}
