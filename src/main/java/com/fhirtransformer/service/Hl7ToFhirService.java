package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.config.TenantContext;
import com.fhirtransformer.util.MappingConstants;
import com.fhirtransformer.util.DateTimeUtil;
import com.fhirtransformer.service.converter.*;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.DiagnosticReport;

import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.text.SimpleDateFormat;

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

    @Autowired
    public Hl7ToFhirService(FhirValidationService fhirValidationService, FhirContext fhirContext,
            HapiContext hapiContext, MeterRegistry meterRegistry,
            PatientConverter patientConverter, EncounterConverter encounterConverter,
            ObservationConverter observationConverter, AllergyConverter allergyConverter) {
        this.hl7Context = hapiContext;
        this.fhirContext = fhirContext;
        this.fhirValidationService = fhirValidationService;
        this.meterRegistry = meterRegistry;
        this.patientConverter = patientConverter;
        this.encounterConverter = encounterConverter;
        this.observationConverter = observationConverter;
        this.allergyConverter = allergyConverter;
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

            String patientId = UUID.randomUUID().toString();
            ConversionContext context = ConversionContext.builder()
                    .patientId(patientId)
                    .hapiMessage(hapiMsg)
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

            // Map Next of Kin (NK1)
            processNextOfKin(terser, bundle, patientId);

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
            processConditions(terser, bundle, patientId);

            // Map AL1 segments (Allergies)
            List<AllergyIntolerance> allergies = allergyConverter.convert(terser, bundle, context);
            for (AllergyIntolerance allergy : allergies) {
                bundle.addEntry().setResource(allergy).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("AllergyIntolerance");
            }

            // Map Medication segments (RXE, RXO, RXA)
            processMedications(terser, bundle, patient);
            processDiagnosticReports(terser, bundle, patient);
            processImmunizations(terser, bundle, patient);
            processAppointments(terser, bundle, patient);
            processServiceRequests(terser, bundle, patient);

            // Map IN1 segments (Insurance)
            processInsurance(terser, bundle, patientId);

            // Map PR1 segments (Procedures)
            processProcedures(terser, bundle, patientId);

            // Map NTE segments (Notes)
            processNotes(terser, bundle, patientId);

            // Map GT1 segments (Guarantor)
            processGuarantor(terser, bundle, patientId);

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

    private void processConditions(Terser terser, Bundle bundle, String patientId) {
        int dg1Index = 0;
        while (true) {
            try {
                String dg1Path = "/.DG1(" + dg1Index + ")";
                String diagnosisCode = terser.get(dg1Path + "-3-1");

                if (diagnosisCode == null)
                    break;

                Condition condition = new Condition();
                condition.setId(UUID.randomUUID().toString());
                condition.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));
                condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding()
                        .setSystem(MappingConstants.SYSTEM_CONDITION_VER_STATUS)
                        .setCode(MappingConstants.CODE_CONFIRMED)));
                condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding()
                        .setSystem(MappingConstants.SYSTEM_CONDITION_CLINICAL).setCode(MappingConstants.CODE_ACTIVE)));

                String diagnosisName = terser.get(dg1Path + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem("http://hl7.org/fhir/sid/icd-10").setCode(diagnosisCode)
                        .setDisplay(diagnosisName);
                condition.setCode(code);

                String type = terser.get(dg1Path + "-6");
                if (type != null && !type.isEmpty()) {
                    CodeableConcept category = new CodeableConcept();
                    category.addCoding().setSystem(MappingConstants.SYSTEM_CONDITION_CATEGORY)
                            .setCode("encounter-diagnosis").setDisplay(type);
                    condition.addCategory(category);
                }

                bundle.addEntry().setResource(condition).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Condition");
                dg1Index++;
            } catch (Exception e) {
                break;
            }
        }
    }

    private void processMedications(Terser terser, Bundle bundle, Patient patient) {
        // HL7 v2.5 Pharmacy/Treatment segments: RXE, RXO, RXA
        String[] medSegments = { "RXE", "RXO", "RXA" };

        for (String segmentName : medSegments) {
            try {
                int segmentCount = -1;
                while (true) {
                    segmentCount++;
                    String segmentPath = "/." + segmentName + "(" + segmentCount + ")";
                    if (segmentCount > 50) {
                        log.warn("Max medication segments reached for {}", segmentName);
                        break;
                    }
                    try {
                        // Check if segment exists by trying to access it
                        // HAPI Terser usually throws if not found, but we add depth check safety
                        terser.getSegment(segmentPath);
                        // Additionally check if the segment actually has content if possible,
                        // but getSegment throwing is the standard check.
                        // The loop limit is the primary safety net here against OOM.
                    } catch (Exception e) {
                        break; // No more segments of this type
                    }

                    log.debug("Processing medication group {}", segmentPath);

                    MedicationRequest medRequest = new MedicationRequest();
                    medRequest.setId(UUID.randomUUID().toString());
                    medRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
                    medRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
                    medRequest.setSubject(new Reference(patient));

                    // Map Medication Code
                    String code = null;
                    String display = null;
                    String system = "http://www.nlm.nih.gov/research/umls/rxnorm"; // Default to RxNorm

                    if ("RXE".equals(segmentName)) {
                        code = terser.get(segmentPath + "-2-1");
                        display = terser.get(segmentPath + "-2-2");

                        // Fallback if component parsing fails but field exists
                        if (code == null) {
                            String raw = terser.get(segmentPath + "-2");
                            log.info("RXE-2-1 is null. Raw RXE-2: {}", raw);
                            if (raw != null && raw.contains("^")) {
                                code = raw.split("\\^")[0];
                            } else {
                                code = raw; // Use whole field if no caret
                            }
                        }
                    } else if ("RXO".equals(segmentName)) {
                        code = terser.get(segmentPath + "-1-1");
                        display = terser.get(segmentPath + "-1-2");
                    } else if ("RXA".equals(segmentName)) {
                        code = terser.get(segmentPath + "-5-1");
                        display = terser.get(segmentPath + "-5-2");
                        medRequest.setStatus(MedicationRequest.MedicationRequestStatus.COMPLETED);

                        // Authored On (Administration Date)
                        String adminDate = terser.get(segmentPath + "-3");
                        if (adminDate != null && !adminDate.isEmpty()) {
                            try {
                                medRequest.setAuthoredOn(DateTimeUtil.hl7DateTimeToFhir(adminDate).getValue());
                            } catch (Exception e) {
                                log.warn("Failed to parse administration date: {}", adminDate);
                            }
                        }
                    }

                    if (code != null) {
                        CodeableConcept medication = new CodeableConcept();
                        Coding coding = new Coding();
                        coding.setSystem(system);
                        coding.setCode(code);
                        if (display != null)
                            coding.setDisplay(display);
                        medication.addCoding(coding);
                        medRequest.setMedication(medication);
                    } else {
                        log.warn("Skipping MedicationRequest for segment {} due to missing code", segmentPath);
                        continue; // Skip invalid resource to prevent validation error
                    }

                    // Dosage Instructions
                    Dosage dosage = new Dosage();
                    boolean hasDosageData = false;

                    if ("RXE".equals(segmentName)) {
                        String doseAmount = terser.get(segmentPath + "-3");
                        String doseUnits = terser.get(segmentPath + "-5-1"); // CE.identifier

                        if (doseAmount != null && doseUnits != null) {
                            try {
                                Quantity doseQuantity = new Quantity();
                                doseQuantity.setValue(Double.parseDouble(doseAmount));
                                doseQuantity.setUnit(doseUnits);
                                doseQuantity.setSystem("http://unitsofmeasure.org");
                                var doseAndRate = dosage.addDoseAndRate();
                                doseAndRate.setDose(doseQuantity);
                                hasDosageData = true;
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse dose amount: {}", doseAmount);
                            }
                        }

                        // RXE-7 Provider's Administration Instructions
                        // RXE-7 Provider's Administration Instructions
                        String instructions = terser.get(segmentPath + "-7-2"); // CE.text
                        if (instructions == null || instructions.isEmpty()) {
                            instructions = terser.get(segmentPath + "-7-1"); // Fallback to Identifier
                        }
                        if (instructions == null || instructions.isEmpty()) {
                            instructions = terser.get(segmentPath + "-7"); // Fallback to whole field
                        }

                        if (instructions != null && !instructions.isEmpty()) {
                            dosage.setText(instructions);
                            hasDosageData = true;
                        }

                        // RXE-21 Give Rate Amount
                        String rateAmount = terser.get(segmentPath + "-21");
                        // RXE-22 Give Rate Units
                        String rateUnits = terser.get(segmentPath + "-22-1");
                        if (rateAmount != null && rateUnits != null) {
                            try {
                                Quantity rateQuantity = new Quantity();
                                rateQuantity.setValue(Double.parseDouble(rateAmount));
                                rateQuantity.setUnit(rateUnits);
                                var rateComp = dosage.addDoseAndRate(); // This might create a second component if dose
                                                                        // was already set, simplified for now
                                rateComp.setRate(rateQuantity);
                                hasDosageData = true;
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse rate amount: {}", rateAmount);
                            }
                        }

                        // RXE-10 Dispense Amount
                        String dispenseAmount = terser.get(segmentPath + "-10");
                        // RXE-11 Dispense Units
                        String dispenseUnits = terser.get(segmentPath + "-11-1");
                        // RXE-12 Number of Refills
                        String refills = terser.get(segmentPath + "-12");

                        if ((dispenseAmount != null && !dispenseAmount.isEmpty())
                                || (refills != null && !refills.isEmpty())) {
                            MedicationRequest.MedicationRequestDispenseRequestComponent dispenseRequest = new MedicationRequest.MedicationRequestDispenseRequestComponent();

                            if (dispenseAmount != null) {
                                try {
                                    Quantity quantity = new Quantity();
                                    quantity.setValue(Double.parseDouble(dispenseAmount));
                                    if (dispenseUnits != null)
                                        quantity.setUnit(dispenseUnits);
                                    dispenseRequest.setQuantity(quantity);
                                } catch (NumberFormatException e) {
                                    log.warn("Could not parse dispense amount: {}", dispenseAmount);
                                }
                            }

                            if (refills != null) {
                                try {
                                    dispenseRequest.setNumberOfRepeatsAllowed(Integer.parseInt(refills));
                                } catch (NumberFormatException e) {
                                    log.warn("Could not parse refills: {}", refills);
                                }
                            }
                            medRequest.setDispenseRequest(dispenseRequest);
                        }

                    } else if ("RXO".equals(segmentName)) {
                        String doseAmount = terser.get(segmentPath + "-2");
                        String doseUnits = terser.get(segmentPath + "-4-1");

                        if (doseAmount != null && doseUnits != null) {
                            try {
                                Quantity doseQuantity = new Quantity();
                                doseQuantity.setValue(Double.parseDouble(doseAmount));
                                doseQuantity.setUnit(doseUnits);
                                var doseAndRate = dosage.addDoseAndRate();
                                doseAndRate.setDose(doseQuantity);
                                hasDosageData = true;
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse RXO dose amount: {}", doseAmount);
                            }
                        }
                    } else if ("RXA".equals(segmentName)) {
                        String doseAmount = terser.get(segmentPath + "-6");
                        String doseUnits = terser.get(segmentPath + "-7-1");
                        String dosageForm = terser.get(segmentPath + "-8-2"); // Text description

                        if (doseAmount != null && doseUnits != null) {
                            try {
                                Quantity doseQuantity = new Quantity();
                                doseQuantity.setValue(Double.parseDouble(doseAmount));
                                doseQuantity.setUnit(doseUnits);
                                var doseAndRate = dosage.addDoseAndRate();
                                doseAndRate.setDose(doseQuantity);
                                hasDosageData = true;
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse RXA dose amount: {}", doseAmount);
                            }
                        }

                        if (dosageForm != null && !dosageForm.isEmpty()) {
                            dosage.setText(dosageForm);
                            hasDosageData = true;
                        }
                    }

                    if (hasDosageData) {
                        medRequest.addDosageInstruction(dosage);
                    }

                    bundle.addEntry().setResource(medRequest).getRequest()
                            .setMethod(Bundle.HTTPVerb.POST)
                            .setUrl("MedicationRequest");

                    log.debug("Mapped MedicationRequest: {}", medRequest.getId());
                }
            } catch (Exception e) {
                log.error("Error processing " + segmentName + " segments", e);
            }
        }
    }

    private void processDiagnosticReports(Terser terser, Bundle bundle, Patient patient) {
        log.info("ProcessDiagnosticReports called");
        int obrIndex = 0;
        while (true) {
            try {
                String obrPath = "/.OBR(" + obrIndex + ")";
                // Check existence
                try {
                    if (terser.getSegment(obrPath) == null)
                        break;
                    log.info("Found OBR segment at {}", obrPath);
                } catch (Exception e) {
                    break;
                }

                if (obrIndex > 50) {
                    log.warn("Max OBR segments reached");
                    break;
                }

                DiagnosticReport report = new DiagnosticReport();
                report.setId(UUID.randomUUID().toString());
                report.setSubject(new Reference(patient));

                // OBR-4 Universal Service ID -> Code
                String code = terser.get(obrPath + "-4-1");
                String display = terser.get(obrPath + "-4-2");
                if (code != null) {
                    CodeableConcept cc = new CodeableConcept();
                    cc.addCoding().setSystem(MappingConstants.SYSTEM_LOINC).setCode(code).setDisplay(display);
                    report.setCode(cc);
                } else {
                    log.debug("Skipping DiagnosticReport for OBR at {} due to missing code", obrPath);
                    obrIndex++;
                    continue;
                }

                // OBR-7 Observation Date/Time -> EffectiveDateTime
                String obsDate = terser.get(obrPath + "-7");
                if (obsDate != null && !obsDate.isEmpty()) {
                    try {
                        report.setEffective(DateTimeUtil.hl7DateTimeToFhir(obsDate));
                    } catch (Exception e) {
                        log.warn("Failed to parse OBR-7 date: {}", obsDate);
                    }
                }

                // OBR-22 Status Change Date/Time -> Issued
                String issuedDate = terser.get(obrPath + "-22");
                if (issuedDate != null && !issuedDate.isEmpty()) {
                    try {
                        report.setIssued(DateTimeUtil.hl7DateTimeToFhir(issuedDate).getValue());
                    } catch (Exception e) {
                        log.warn("Failed to parse OBR-22 date: {}", issuedDate);
                    }
                }

                // OBR-25 Result Status -> Status
                String status = terser.get(obrPath + "-25");
                if (status != null) {
                    switch (status) {
                        case "F":
                            report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
                            break;
                        case "C":
                            report.setStatus(DiagnosticReport.DiagnosticReportStatus.CORRECTED);
                            break;
                        case "X":
                            report.setStatus(DiagnosticReport.DiagnosticReportStatus.CANCELLED);
                            break;
                        case "P":
                            report.setStatus(DiagnosticReport.DiagnosticReportStatus.PRELIMINARY);
                            break;
                        default:
                            report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
                            break;
                    }
                } else {
                    report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
                }

                // OBR-2/3 Identifiers
                String placerId = terser.get(obrPath + "-2");
                if (placerId != null) {
                    report.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setValue(placerId)
                            .getType().addCoding().setCode("PLAC").setDisplay("Placer Identifier");
                }
                String fillerId = terser.get(obrPath + "-3");
                if (fillerId != null) {
                    report.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setValue(fillerId)
                            .getType().addCoding().setCode("FILL").setDisplay("Filler Identifier");
                }

                bundle.addEntry().setResource(report).getRequest()
                        .setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("DiagnosticReport");

                obrIndex++;
            } catch (Exception e) {
                log.error("Error processing OBR segment", e);
                break;
            }
        }
    }

    private void processNextOfKin(Terser terser, Bundle bundle, String patientId) {
        int nk1Index = 0;
        while (true) {
            try {
                String nk1Path = "/.NK1(" + nk1Index + ")";
                String lastName = terser.get(nk1Path + "-2-1"); // NK1-2 Name

                if (lastName == null)
                    break;

                // We need to find the Patient resource in the bundle to add contact
                // Since this is a specialized service, we know we just added it.
                // Improve: pass Patient object or find it cleanly.
                // For now, let's find the Patient resource in the bundle we are building
                Patient patient = null;
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry.getResource() instanceof Patient) {
                        patient = (Patient) entry.getResource();
                        break;
                    }
                }

                if (patient != null) {
                    Patient.ContactComponent contact = patient.addContact();

                    // Name
                    String firstName = terser.get(nk1Path + "-2-2");
                    HumanName name = new HumanName().setFamily(lastName);
                    if (firstName != null)
                        name.addGiven(firstName);
                    contact.setName(name);

                    // Relationship (NK1-3)
                    String relCode = terser.get(nk1Path + "-3-1");
                    String relText = terser.get(nk1Path + "-3-2");
                    if (relCode != null) {
                        CodeableConcept relationship = new CodeableConcept();
                        relationship.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0063")
                                .setCode(relCode).setDisplay(relText);
                        contact.addRelationship(relationship);
                    }

                    // Phone (NK1-5)
                    String phone = terser.get(nk1Path + "-5-1");
                    if (phone != null && !phone.isEmpty()) {
                        contact.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(phone);
                    }

                    // Address (NK1-4)
                    String street = terser.get(nk1Path + "-4-1");
                    String city = terser.get(nk1Path + "-4-3");
                    String state = terser.get(nk1Path + "-4-4");
                    String zip = terser.get(nk1Path + "-4-5");
                    if (street != null || city != null || state != null || zip != null) {
                        Address address = contact.getAddress();
                        if (street != null)
                            address.addLine(street);
                        if (city != null)
                            address.setCity(city);
                        if (state != null)
                            address.setState(state);
                        if (zip != null)
                            address.setPostalCode(zip);
                    }
                }

                nk1Index++;
            } catch (Exception e) {
                break;
            }
        }
    }

    private void processInsurance(Terser terser, Bundle bundle, String patientId) {
        int in1Index = 0;
        while (true) {
            try {
                String in1Path = "/.IN1(" + in1Index + ")";
                String planId = terser.get(in1Path + "-2-1"); // IN1-2 Insurance Plan ID

                if (planId == null && in1Index > 0)
                    break; // Check first rep might be null if segment missing
                // Terser throws exception or returns null if segment not exists?
                // Normally we check a required field. If Plan ID is null, maybe just no
                // segment.
                // Better approach: check rep 0. If exception, break.
                // Standard loop pattern:

                // IN1-3 Insurance Company ID
                String companyId = terser.get(in1Path + "-3-1");
                // If the most basic fields are missing, assume end of segments
                if (planId == null && companyId == null)
                    break;

                Coverage coverage = new Coverage();
                coverage.setId(UUID.randomUUID().toString());
                coverage.setStatus(Coverage.CoverageStatus.ACTIVE);
                String subId = terser.get(in1Path + "-36-1");
                if (subId == null || subId.isEmpty()) {
                    subId = terser.get(in1Path + "-36");
                }
                log.debug("Processing IN1-36 (Subscriber ID) for index {}: '{}'", in1Index, subId);
                coverage.setSubscriberId(subId); // IN1-36 Policy Number

                // Beneficiary
                coverage.setBeneficiary(new Reference("Patient/" + patientId));

                // Payor (Organization)
                if (companyId != null) {
                    Organization payor = new Organization();
                    payor.setId(UUID.randomUUID().toString());
                    payor.addIdentifier().setValue(companyId);
                    payor.setName(terser.get(in1Path + "-4-1")); // IN1-4 Company Name

                    bundle.addEntry().setResource(payor).getRequest().setMethod(Bundle.HTTPVerb.POST)
                            .setUrl("Organization");
                    coverage.addPayor(new Reference("Organization/" + payor.getId()));
                }

                // Type
                String planType = terser.get(in1Path + "-47");
                if (planType != null) {
                    CodeableConcept type = new CodeableConcept();
                    type.addCoding().setSystem(MappingConstants.SYSTEM_COVERAGE_TYPE).setCode(planType);
                    coverage.setType(type);
                }

                bundle.addEntry().setResource(coverage).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Coverage");

                in1Index++;
            } catch (Exception e) {
                break;
            }
        }
    }

    private void processProcedures(Terser terser, Bundle bundle, String patientId) {
        int pr1Index = 0;
        while (true) {
            try {
                String pr1Path = "/.PR1(" + pr1Index + ")";
                String codeVal = terser.get(pr1Path + "-3-1");

                if (codeVal == null)
                    break;

                Procedure procedure = new Procedure();
                procedure.setId(UUID.randomUUID().toString());
                procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
                procedure.setSubject(new Reference("Patient/" + patientId));

                // Procedure Code
                String codeText = terser.get(pr1Path + "-3-2");
                CodeableConcept code = new CodeableConcept();
                // Defaulting system to CPT or similar for now, or just generic
                code.addCoding().setSystem(MappingConstants.SYSTEM_CPT).setCode(codeVal).setDisplay(codeText);
                procedure.setCode(code);

                // PR1-5 Procedure Date/Time
                String procDate = terser.get(pr1Path + "-5");
                if (procDate != null && !procDate.isEmpty()) {
                    Date date = parseHl7Date(procDate);
                    if (date != null) {
                        procedure.setPerformed(new org.hl7.fhir.r4.model.DateTimeType(date));
                    }
                }

                // Surgeon/Performer? PR1-11
                // ...

                bundle.addEntry().setResource(procedure).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Procedure");

                pr1Index++;
            } catch (Exception e) {
                break;
            }
        }
    }

    private void processNotes(Terser terser, Bundle bundle, String patientId) {
        // In HL7 v2, NTEs usually follow the segment they comment on.
        // However, extracting them contextually (e.g. NTE after PID, NTE after OBX) is
        // complex with just repeating loop.
        // A common simplification for ADT: NTEs often relate to the Patient or
        // Encounter.
        // Or we can scan for global NTE segments.
        // Given Terser limitations for "next segment" logic without iteration,
        // we will implement a basic global NTE extraction and attach to
        // Patient/Encounter as "remarks" or similar.
        // BUT, NTEs are repeating.
        // Let's attach general notes to the Patient resource for now, or just extract
        // them.

        int nteIndex = 0;
        while (true) {
            try {
                String ntePath = "/.NTE(" + nteIndex + ")";
                String comment = terser.get(ntePath + "-3");

                if (comment == null && nteIndex > 0)
                    break;
                // First one might be null if segment missing.
                if (comment == null)
                    break;

                // Find Patient Resource
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry.getResource() instanceof Patient) {
                        // Do not add multiple times if loop runs?
                        // Actually we are iterating NTEs.
                        // Ideally, check if comment already exists?
                        // Simplest: just add it.
                    }
                }

                // Better strategy: Since we don't know which segment the NTE belongs to easily
                // with Terser absolute paths
                // (unless we track segment index), we might just map global NTEs to the Patient
                // or Encounter text for now.
                // Let's create a generic "Observation" for the note, or append to Patient.

                // Appending to Patient text/narrative is complex in code.
                // How about creating a specific Observation for "Clinical Note"?
                // Or simply logging it?
                // Standard practice: NTE following PID -> Patient Note (Observation or
                // Patient.text)
                // NTE following PV1 -> Encounter Note.

                // For simplicity in this transformer: We will map ALL NTEs found to a "Comment"
                // Observation linked to the Patient.
                Observation noteObs = new Observation();
                noteObs.setId(UUID.randomUUID().toString());
                noteObs.setStatus(Observation.ObservationStatus.FINAL);
                noteObs.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));
                noteObs.setCode(new CodeableConcept()
                        .addCoding(new Coding().setSystem("http://loinc.org").setCode("34109-9").setDisplay("Note")));
                noteObs.setValue(new org.hl7.fhir.r4.model.StringType(comment));

                bundle.addEntry().setResource(noteObs).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Observation");

                nteIndex++;
            } catch (Exception e) {
                break;
            }
        }
    }

    private void processGuarantor(Terser terser, Bundle bundle, String patientId) {
        int gt1Index = 0;
        while (true) {
            try {
                String gt1Path = "/.GT1(" + gt1Index + ")";
                String guarantorName = terser.get(gt1Path + "-3-1"); // Family Name

                if (guarantorName == null && gt1Index > 0)
                    break;
                if (guarantorName == null)
                    break;

                RelatedPerson rp = new RelatedPerson();
                rp.setId(UUID.randomUUID().toString());
                rp.setPatient(new Reference("Patient/" + patientId));
                rp.setActive(true);

                // GT1-3 Name
                HumanName name = new HumanName();
                name.setFamily(guarantorName);
                String given = terser.get(gt1Path + "-3-2");
                if (given != null)
                    name.addGiven(given);
                rp.addName(name);

                // GT1-5 Address
                String addrLine = terser.get(gt1Path + "-5-1");
                if (addrLine != null) {
                    Address address = new Address();
                    address.addLine(addrLine);
                    address.setCity(terser.get(gt1Path + "-5-3"));
                    address.setState(terser.get(gt1Path + "-5-4"));
                    address.setPostalCode(terser.get(gt1Path + "-5-5"));
                    rp.addAddress(address);
                }

                // GT1-6 Phone
                String phone = terser.get(gt1Path + "-6-1");
                if (phone != null) {
                    rp.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(phone);
                }

                // GT1-11 Relationship
                // Map code if possible, or just text
                String relCode = terser.get(gt1Path + "-11-1"); // Code
                if (relCode != null) {
                    // Try to map to standard v3 RoleCode if possible, otherwise just use local
                    // Common: GT -> Guarantor, EP -> Emergency Contact, etc.
                    CodeableConcept relation = new CodeableConcept();
                    relation.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0063").setCode(relCode);
                    rp.addRelationship(relation);
                }

                bundle.addEntry().setResource(rp).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("RelatedPerson");

                gt1Index++;
            } catch (Exception e) {
                break;
            }
        }
    }

    // --- Robust Helper Methods ---

    private Date parseHl7Date(String hl7Date) {
        if (hl7Date == null || hl7Date.isEmpty())
            return null;
        try {
            // Check for timezone offset
            String pattern = "yyyyMMdd";
            if (hl7Date.length() >= 14)
                pattern = "yyyyMMddHHmmss";
            else if (hl7Date.length() >= 12)
                pattern = "yyyyMMddHHmm";

            // Simple handling of standard HL7 dates without complex TZ for now,
            // but stripping everything after '+' or '-' if needed.
            String cleanDate = hl7Date;
            if (cleanDate.contains("+"))
                cleanDate = cleanDate.substring(0, cleanDate.indexOf("+"));
            else if (cleanDate.contains("-") && cleanDate.length() > 8)
                cleanDate = cleanDate.substring(0, cleanDate.indexOf("-"));

            if (cleanDate.length() > pattern.length()) {
                cleanDate = cleanDate.substring(0, pattern.length());
            }

            return new SimpleDateFormat(pattern).parse(cleanDate);
        } catch (Exception e) {
            log.warn("Failed to parse HL7 date: {}", hl7Date);
        }
        return null;
    }

    private void processImmunizations(Terser terser, Bundle bundle, Patient patient) throws Exception {
        log.debug("Processing Immunization segments...");
        int index = 0;
        while (true) {
            try {
                String rxaPath = "/.RXA(" + index + ")";
                String vaccineCode = terser.get(rxaPath + "-5-1");
                if (vaccineCode == null)
                    break;

                Immunization immunization = new Immunization();
                immunization.setId(UUID.randomUUID().toString());

                // Status
                String status = terser.get(rxaPath + "-20");
                if ("CP".equals(status)) {
                    immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED);
                } else if ("NA".equals(status)) {
                    immunization.setStatus(Immunization.ImmunizationStatus.NOTDONE);
                } else {
                    immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED); // Default
                }

                // Vaccine Code
                immunization.getVaccineCode().addCoding()
                        .setSystem(MappingConstants.SYSTEM_CVX)
                        .setCode(vaccineCode)
                        .setDisplay(terser.get(rxaPath + "-5-2"));

                // Patient
                immunization.setPatient(new Reference("Patient/" + patient.getId()));

                // Date/Time
                String adminDate = terser.get(rxaPath + "-3");
                if (adminDate != null && !adminDate.isEmpty()) {
                    DateTimeType dateType = DateTimeUtil.hl7DateTimeToFhir(adminDate);
                    if (dateType != null) {
                        immunization.setOccurrence(dateType);
                    }
                }

                // Lot Number
                String lot = terser.get(rxaPath + "-15");
                if (lot != null)
                    immunization.setLotNumber(lot);

                // Manufacturer
                String manufacturerName = terser.get(rxaPath + "-17-2");
                if (manufacturerName != null) {
                    Reference manufacturerRef = processOrganization(terser, rxaPath + "-17", bundle);
                    immunization.setManufacturer(manufacturerRef);
                }

                // Performer
                String performerId = terser.get(rxaPath + "-10-1");
                if (performerId != null) {
                    Reference performerRef = processPractitioner(terser, rxaPath + "-10", bundle);
                    immunization.addPerformer().setActor(performerRef);
                }

                bundle.addEntry().setResource(immunization).getRequest()
                        .setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Immunization");
                index++;
            } catch (Exception e) {
                break;
            }
        }
    }

    private Reference processPractitioner(Terser terser, String path, Bundle bundle) throws Exception {
        String id = terser.get(path + "-1");
        if (id == null)
            return null;

        // Check if already in bundle to avoid duplicates
        Optional<Bundle.BundleEntryComponent> existing = bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof Practitioner && e.getResource().getId().contains(id))
                .findFirst();

        if (existing.isPresent()) {
            return new Reference("Practitioner/" + existing.get().getResource().getId());
        }

        Practitioner practitioner = new Practitioner();
        practitioner.setId(UUID.randomUUID().toString());
        practitioner.addIdentifier()
                .setSystem(MappingConstants.SYSTEM_PRACTITIONER_ID)
                .setValue(id);

        String family = terser.get(path + "-2");
        String given = terser.get(path + "-3");
        if (family != null || given != null) {
            HumanName name = practitioner.addName().setFamily(family);
            if (given != null)
                name.addGiven(given);
        }

        bundle.addEntry().setResource(practitioner).getRequest()
                .setMethod(Bundle.HTTPVerb.POST)
                .setUrl("Practitioner");

        return new Reference("Practitioner/" + practitioner.getId());
    }

    private Reference processOrganization(Terser terser, String path, Bundle bundle) throws Exception {
        String id = terser.get(path + "-1");
        String name = terser.get(path + "-2");
        if (name == null)
            name = id;
        if (name == null)
            return null;

        // Simple deduplication by name
        final String finalName = name;
        Optional<Bundle.BundleEntryComponent> existing = bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof Organization
                        && ((Organization) e.getResource()).getName().equals(finalName))
                .findFirst();

        if (existing.isPresent()) {
            return new Reference("Organization/" + existing.get().getResource().getId());
        }

        Organization org = new Organization();
        org.setId(UUID.randomUUID().toString());
        org.setName(name);

        bundle.addEntry().setResource(org).getRequest()
                .setMethod(Bundle.HTTPVerb.POST)
                .setUrl("Organization");

        return new Reference("Organization/" + org.getId());
    }

    private void processAppointments(Terser terser, Bundle bundle, Patient patient) throws Exception {
        try {
            log.debug("Processing Appointment segments...");
            String schPath = "/.SCH";
            String fillerId = terser.get(schPath + "-2");
            if (fillerId == null)
                return;

            Appointment appointment = new Appointment();
            appointment.setId(UUID.randomUUID().toString());
            appointment.setStatus(Appointment.AppointmentStatus.BOOKED);

            // Identifiers
            if (fillerId != null)
                appointment.addIdentifier().setValue(fillerId);
            String placerId = terser.get(schPath + "-1");
            if (placerId != null)
                appointment.addIdentifier().setValue(placerId);

            // Reason
            String reasonStr = terser.get(schPath + "-6-2");
            if (reasonStr != null) {
                appointment.addReasonCode().setText(reasonStr);
            }

            // Schedule Timing
            String start = terser.get(schPath + "-11-4");
            if (start != null) {
                DateTimeType dateType = DateTimeUtil.hl7DateTimeToFhir(start);
                if (dateType != null)
                    appointment.setStart(dateType.getValue());
            }

            appointment.addParticipant()
                    .setActor(new Reference("Patient/" + patient.getId()))
                    .setStatus(Appointment.ParticipationStatus.ACCEPTED);

            bundle.addEntry().setResource(appointment).getRequest()
                    .setMethod(Bundle.HTTPVerb.POST)
                    .setUrl("Appointment");
        } catch (Exception e) {
            log.debug("No Appointment (SCH) segment found or error processing it: {}", e.getMessage());
        }
    }

    private void processServiceRequests(Terser terser, Bundle bundle, Patient patient) throws Exception {
        log.debug("Processing ServiceRequest segments...");
        int index = 0;
        while (true) {
            try {
                String obrPath = "/.OBR(" + index + ")";
                String code = terser.get(obrPath + "-4-1");
                if (code == null)
                    break;

                ServiceRequest sr = new ServiceRequest();
                sr.setId(UUID.randomUUID().toString());
                sr.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
                sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);

                // Priority
                String priority = terser.get(obrPath + "-5");
                if ("S".equals(priority))
                    sr.setPriority(ServiceRequest.ServiceRequestPriority.STAT);
                else if ("A".equals(priority))
                    sr.setPriority(ServiceRequest.ServiceRequestPriority.URGENT);
                else
                    sr.setPriority(ServiceRequest.ServiceRequestPriority.ROUTINE);

                sr.getCode().addCoding()
                        .setCode(code)
                        .setDisplay(terser.get(obrPath + "-4-2"));

                sr.setSubject(new Reference("Patient/" + patient.getId()));

                bundle.addEntry().setResource(sr).getRequest()
                        .setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("ServiceRequest");
                index++;
            } catch (Exception e) {
                break;
            }
        }
    }
}
