package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.config.TenantContext;
import com.fhirtransformer.util.MappingConstants;
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
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.StringType;
import ca.uhn.hl7v2.model.Segment;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Autowired
    public Hl7ToFhirService(FhirValidationService fhirValidationService, FhirContext fhirContext,
            HapiContext hapiContext, MeterRegistry meterRegistry) {
        this.hl7Context = hapiContext;
        this.fhirContext = fhirContext;
        this.fhirValidationService = fhirValidationService;
        this.meterRegistry = meterRegistry;
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

            // Extract Patient Data
            Patient patient = processPatient(terser, bundle, hapiMsg);
            String patientId = patient.getIdElement().getIdPart();

            // Map Next of Kin (NK1)
            processNextOfKin(terser, bundle, patientId);

            // Map Encounter Data
            processEncounter(terser, bundle, patientId);

            // Map OBX segments (Observations)
            processObservations(terser, bundle, patientId);

            // Map DG1 segments (Conditions)
            processConditions(terser, bundle, patientId);

            // Map AL1 segments (Allergies)
            processAllergies(terser, bundle, patientId);

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

    private Patient processPatient(Terser terser, Bundle bundle, Message hapiMsg) throws Exception {
        log.debug("Processing Patient segment...");
        Patient patient = new Patient();
        String patientId = UUID.randomUUID().toString();
        patient.setId(patientId);

        // PID-3 Patient Identifiers (Repeating)
        int idIndex = 0;
        while (true) {
            String pid3_1 = terser.get("/.PID-3(" + idIndex + ")-1");
            if (pid3_1 == null)
                break;
            String pid3_4 = terser.get("/.PID-3(" + idIndex + ")-4"); // Assigning Authority
            String pid3_5 = terser.get("/.PID-3(" + idIndex + ")-5"); // Identifier Type Code

            Identifier identifier = patient.addIdentifier().setValue(pid3_1);
            if (pid3_4 != null)
                identifier.setSystem("urn:oid:" + pid3_4);
            else
                identifier.setSystem(MappingConstants.SYSTEM_PATIENT_IDENTIFIER);

            if (pid3_5 != null) {
                identifier.getType().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                        .setCode(pid3_5);
                if ("MR".equals(pid3_5)) {
                    identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
                }
            }
            idIndex++;
        }

        // PID-5 Patient Names (Repeating)
        int nameIndex = 0;
        while (true) {
            String familyName = terser.get("/.PID-5(" + nameIndex + ")-1");
            String givenName = terser.get("/.PID-5(" + nameIndex + ")-2");
            if (familyName == null && givenName == null)
                break;

            HumanName name = patient.addName().setFamily(familyName);
            if (givenName != null)
                name.addGiven(givenName);

            String middleName = terser.get("/.PID-5(" + nameIndex + ")-3");
            if (middleName != null)
                name.addGiven(middleName);

            String suffix = terser.get("/.PID-5(" + nameIndex + ")-4");
            if (suffix != null)
                name.addSuffix(suffix);

            String prefix = terser.get("/.PID-5(" + nameIndex + ")-5");
            if (prefix != null)
                name.addPrefix(prefix);

            String nameType = terser.get("/.PID-5(" + nameIndex + ")-7");
            if (nameType != null) {
                try {
                    name.setUse(HumanName.NameUse.fromCode(nameType.toLowerCase()));
                } catch (Exception e) {
                }
            }
            nameIndex++;
        }

        String gender = terser.get("/.PID-8");
        if ("M".equalsIgnoreCase(gender)) {
            patient.setGender(Enumerations.AdministrativeGender.MALE);
        } else if ("F".equalsIgnoreCase(gender)) {
            patient.setGender(Enumerations.AdministrativeGender.FEMALE);
        } else if ("O".equalsIgnoreCase(gender)) {
            patient.setGender(Enumerations.AdministrativeGender.OTHER);
        } else {
            patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
        }

        String dob = terser.get("/.PID-7");
        if (dob != null && !dob.isEmpty()) {
            patient.setBirthDate(parseHl7Date(dob));
        }

        // PID-10 Race
        String race = terser.get("/.PID-10-1");
        String raceText = terser.get("/.PID-10-2");
        if (race != null) {
            Extension ext = patient.addExtension();
            ext.setUrl("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race");
            ext.addExtension().setUrl("ombCategory")
                    .setValue(new Coding().setSystem(MappingConstants.SYSTEM_RACE).setCode(race).setDisplay(raceText));
        }

        // PID-16 Marital Status
        String maritalStatus = terser.get("/.PID-16-1");
        if (maritalStatus != null) {
            patient.getMaritalStatus().addCoding().setSystem(MappingConstants.SYSTEM_V2_MARITAL_STATUS)
                    .setCode(maritalStatus).setDisplay(terser.get("/.PID-16-2"));
        }

        // PID-17 Religion
        String religion = terser.get("/.PID-17-1");
        if (religion != null) {
            patient.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/patient-religion")
                    .setValue(new CodeableConcept().addCoding().setSystem(MappingConstants.SYSTEM_RELIGION)
                            .setCode(religion).setDisplay(terser.get("/.PID-17-2")));
        }

        // PID-22 Ethnic Group
        String ethnicity = terser.get("/.PID-22-1");
        String ethText = terser.get("/.PID-22-2");
        if (ethnicity != null) {
            Extension ext = patient.addExtension();
            ext.setUrl("http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity");
            ext.addExtension().setUrl("ombCategory")
                    .setValue(new Coding().setSystem(MappingConstants.SYSTEM_ETHNICITY).setCode(ethnicity)
                            .setDisplay(ethText));
        }

        // PID-29/30 Death Details
        String deceased = terser.get("/.PID-29");
        if ("Y".equalsIgnoreCase(deceased)) {
            patient.setDeceased(new BooleanType(true));
            String deathDate = terser.get("/.PID-30");
            if (deathDate != null && !deathDate.isEmpty()) {
                patient.setDeceased(new DateTimeType(parseHl7Date(deathDate)));
            }
        }

        // PD1-4 Primary Care Provider
        String pcpId = terser.get("/.PD1-4-1");
        String pcpName = terser.get("/.PD1-4-2");
        log.info("DEBUG: PD1-4-1='{}', PD1-4-2='{}'", pcpId, pcpName);
        if (pcpId != null || pcpName != null) {
            Reference gp = patient.addGeneralPractitioner();
            if (pcpId != null)
                gp.setReference("Practitioner/" + pcpId);
            if (pcpName != null)
                gp.setDisplay(pcpName);
            else if (pcpId != null && pcpId.length() > 5)
                gp.setDisplay(pcpId);
            log.info("DEBUG: Added GeneralPractitioner: ref='{}', display='{}'", gp.getReference(), gp.getDisplay());
        }

        // PID-11 Addresses (Repeating)
        int addrIndex = 0;
        while (true) {
            String street = terser.get("/.PID-11(" + addrIndex + ")-1");
            String city = terser.get("/.PID-11(" + addrIndex + ")-3");
            if (street == null && city == null)
                break;

            Address address = patient.addAddress();
            if (street != null)
                address.addLine(street);
            String otherLine = terser.get("/.PID-11(" + addrIndex + ")-2");
            if (otherLine != null)
                address.addLine(otherLine);

            address.setCity(city);
            address.setState(terser.get("/.PID-11(" + addrIndex + ")-4"));
            address.setPostalCode(terser.get("/.PID-11(" + addrIndex + ")-5"));
            address.setCountry(terser.get("/.PID-11(" + addrIndex + ")-6"));

            String type = terser.get("/.PID-11(" + addrIndex + ")-7");
            if (type != null) {
                if ("H".equals(type))
                    address.setUse(Address.AddressUse.HOME);
                else if ("O".equals(type) || "B".equals(type))
                    address.setUse(Address.AddressUse.WORK);
            }
            addrIndex++;
        }

        // PID-13/14 Telecom
        processTelecom(terser, "/.PID-13", patient, ContactPoint.ContactPointUse.HOME);
        processTelecom(terser, "/.PID-14", patient, ContactPoint.ContactPointUse.WORK);

        // Z-Segment Processing
        try {
            for (String groupName : hapiMsg.getNames()) {
                if (groupName.startsWith("Z")) {
                    ca.uhn.hl7v2.model.Structure struct = hapiMsg.get(groupName);
                    if (struct instanceof Segment) {
                        Segment seg = (Segment) struct;
                        patient.addExtension()
                                .setUrl(MappingConstants.EXT_HL7_Z_SEGMENT)
                                .setValue(new StringType(seg.encode()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error processing Z-segments: {}", e.getMessage());
        }

        bundle.addEntry().setResource(patient).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");
        return patient;
    }

    private void processEncounter(Terser terser, Bundle bundle, String patientId) {
        try {
            String checkPv1 = terser.get("/.PV1-1");
            log.info("Checking for PV1 segment... PV1-1='{}'", checkPv1);
            if (checkPv1 != null) {
                Encounter encounter = new Encounter();
                encounter.setId(UUID.randomUUID().toString());
                encounter.setStatus(Encounter.EncounterStatus.FINISHED);
                encounter.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));

                String visitNum = terser.get("/.PV1-19");
                log.debug("Processing Encounter for Patient {}: Visit Num='{}'", patientId, visitNum);
                if (visitNum != null) {
                    encounter.addIdentifier().setValue(visitNum);
                }

                // PV1-3 Assigned Patient Location
                String pointOfCare = terser.get("/.PV1-3-1");
                String room = terser.get("/.PV1-3-2");
                String bed = terser.get("/.PV1-3-3");
                if (pointOfCare != null || room != null || bed != null) {
                    StringBuilder locName = new StringBuilder();
                    if (pointOfCare != null)
                        locName.append(pointOfCare);
                    if (room != null)
                        locName.append(" ").append(room);
                    if (bed != null)
                        locName.append("-").append(bed);
                    encounter.addLocation().setLocation(new Reference().setDisplay(locName.toString().trim()));
                }

                String patientClass = terser.get("/.PV1-2");
                if (patientClass != null) {
                    encounter.setClass_(new org.hl7.fhir.r4.model.Coding()
                            .setSystem(MappingConstants.SYSTEM_V2_ACT_CODE).setCode(patientClass));
                }

                // PV1-4 Admission Type
                String admType = terser.get("/.PV1-4");
                if (admType != null) {
                    encounter.addType().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0007")
                            .setCode(admType);
                }

                // PV1-10 Hospital Service
                String hospServ = terser.get("/.PV1-10");
                if (hospServ != null) {
                    CodeableConcept sc = new CodeableConcept();
                    sc.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0069").setCode(hospServ);
                    encounter.setServiceType(sc);
                }

                // Map multiple participants (Attending, Referring, Consulting)
                processParticipants(terser, encounter);

                String admitDateStr = terser.get("/.PV1-44");
                if (admitDateStr == null || admitDateStr.isEmpty()) {
                    admitDateStr = terser.get("/.EVN-2");
                }

                Date admitDate = parseHl7Date(admitDateStr);
                String dischargeDateStr = terser.get("/.PV1-45");
                Date dischargeDate = parseHl7Date(dischargeDateStr);

                if (admitDate != null || dischargeDate != null) {
                    Period period = new Period();
                    if (admitDate != null)
                        period.setStart(admitDate);
                    if (dischargeDate != null)
                        period.setEnd(dischargeDate);
                    encounter.setPeriod(period);
                }

                bundle.addEntry().setResource(encounter).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Encounter");

                // PV2-3 Admit Reason
                try {
                    String reason = terser.get("/.PV2-3-2"); // Text
                    if (reason == null || reason.isEmpty()) {
                        reason = terser.get("/.PV2-3-1"); // Fallback to Identifier
                    }
                    if (reason == null || reason.isEmpty()) {
                        reason = terser.get("/.PV2-3"); // Fallback to whole field
                    }
                    log.debug("Processing PV2-3 (Admit Reason): original='{}', cleaned='{}'",
                            reason, (reason != null ? reason.replace("^", "").trim() : "null"));
                    if (reason != null && !reason.isEmpty()) {
                        // Cleanup carets if whole field returned
                        reason = reason.replace("^", "").trim();
                        encounter.addReasonCode().setText(reason);
                    }
                } catch (Exception ex) {
                    // PV2 might not exist
                }
            }
        } catch (Exception e) {
            log.warn("Error processing PV1 segment: {}", e.getMessage());
        }
    }

    private void processObservations(Terser terser, Bundle bundle, String patientId) {
        int obxIndex = 0;
        while (true) {
            try {
                String obxPath = "/.OBX(" + obxIndex + ")";
                String obx3 = terser.get(obxPath + "-3-1");

                if (obx3 == null)
                    break;

                Observation observation = new Observation();
                observation.setId(UUID.randomUUID().toString());
                observation.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));
                observation.setStatus(Observation.ObservationStatus.FINAL);

                String obx3Text = terser.get(obxPath + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem(MappingConstants.SYSTEM_LOINC).setCode(obx3).setDisplay(obx3Text);
                observation.setCode(code);

                String value = terser.get(obxPath + "-5-1");
                String units = terser.get(obxPath + "-6-1");

                if (value != null && !value.isEmpty()) {
                    try {
                        double val = Double.parseDouble(value);
                        Quantity quantity = new Quantity();
                        quantity.setValue(val);
                        if (units != null)
                            quantity.setUnit(units);
                        observation.setValue(quantity);
                    } catch (NumberFormatException e) {
                        observation.setValue(new org.hl7.fhir.r4.model.StringType(value));
                    }
                }

                String status = terser.get(obxPath + "-11");
                if (status != null) {
                    switch (status) {
                        case "F":
                            observation.setStatus(Observation.ObservationStatus.FINAL);
                            break;
                        case "P":
                            observation.setStatus(Observation.ObservationStatus.PRELIMINARY);
                            break;
                        case "C":
                            observation.setStatus(Observation.ObservationStatus.AMENDED);
                            break;
                        case "X":
                            observation.setStatus(Observation.ObservationStatus.CANCELLED);
                            break;
                        case "W":
                            observation.setStatus(Observation.ObservationStatus.ENTEREDINERROR);
                            break;
                        default:
                            observation.setStatus(Observation.ObservationStatus.FINAL);
                            break;
                    }
                }

                // OBX-8 Interpretation (Abnormal Flags)
                String interpretation = terser.get(obxPath + "-8");
                if (interpretation != null) {
                    observation.addInterpretation().addCoding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                            .setCode(interpretation);
                }

                // OBX-14 Date/Time of the Observation
                String effectiveDateStr = terser.get(obxPath + "-14");
                if (effectiveDateStr != null && !effectiveDateStr.isEmpty()) {
                    Date date = parseHl7Date(effectiveDateStr);
                    if (date != null) {
                        observation.setEffective(new org.hl7.fhir.r4.model.DateTimeType(date));
                    }
                }

                bundle.addEntry().setResource(observation).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Observation");
                obxIndex++;
            } catch (Exception e) {
                break;
            }
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

    private void processAllergies(Terser terser, Bundle bundle, String patientId) {
        int al1Index = 0;
        while (true) {
            try {
                String al1Path = "/.AL1(" + al1Index + ")";
                String allergen = terser.get(al1Path + "-3-1");

                if (allergen == null)
                    break;

                AllergyIntolerance allergy = new AllergyIntolerance();
                allergy.setId(UUID.randomUUID().toString());
                allergy.setPatient(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));
                allergy.setVerificationStatus(new CodeableConcept().addCoding(
                        new Coding().setSystem(MappingConstants.SYSTEM_ALLERGY_VER_STATUS)
                                .setCode(MappingConstants.CODE_CONFIRMED)));
                allergy.setClinicalStatus(new CodeableConcept().addCoding(
                        new Coding().setSystem(MappingConstants.SYSTEM_ALLERGY_CLINICAL)
                                .setCode(MappingConstants.CODE_ACTIVE)));

                // AL1-2 Allergy Type (DA=Drug Allergy, FA=Food Allergy, etc.)
                String type = terser.get(al1Path + "-2");
                if (type != null) {
                    if (MappingConstants.ALLERGY_TYPE_DRUG.equals(type)
                            || MappingConstants.ALLERGY_TYPE_MISC.equals(type)) {
                        allergy.addCategory(
                                AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
                    } else if (MappingConstants.ALLERGY_TYPE_FOOD.equals(type)) {
                        allergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
                    } else if (MappingConstants.ALLERGY_TYPE_ENV.equals(type)) {
                        allergy.addCategory(
                                AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT);
                    }
                }

                // AL1-3 Allergen Code/Text
                String allergenText = terser.get(al1Path + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem("http://hl7.org/fhir/sid/icd-10").setCode(allergen).setDisplay(allergenText);
                code.setText(allergenText);
                allergy.setCode(code);

                AllergyIntolerance.AllergyIntoleranceReactionComponent reactionComp = new AllergyIntolerance.AllergyIntoleranceReactionComponent();
                boolean hasReaction = false;

                // AL1-4 Severity
                String severity = terser.get(al1Path + "-4-1"); // Get first component (SV, MO, MI)
                if (severity == null || severity.isEmpty()) {
                    severity = terser.get(al1Path + "-4"); // Fallback
                }
                log.debug("Processing AL1-4 (Severity) for index {}: '{}'", al1Index, severity);
                if (severity != null) {
                    if (severity.startsWith("MI"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MILD);
                    else if (severity.startsWith("MO"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MODERATE);
                    else if (severity.startsWith("SV"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.SEVERE);
                    hasReaction = true;
                }

                // AL1-5 Reaction
                String reactionText = terser.get(al1Path + "-5");
                if (reactionText != null && !reactionText.isEmpty()) {
                    reactionComp.addManifestation(new CodeableConcept().setText(reactionText));
                    hasReaction = true;
                }

                if (hasReaction) {
                    allergy.addReaction(reactionComp);
                }

                // AL1-6 Identification Date
                String onsetDate = terser.get(al1Path + "-6");
                if (onsetDate != null && !onsetDate.isEmpty()) {
                    Date date = parseHl7Date(onsetDate);
                    if (date != null) {
                        allergy.setOnset(new org.hl7.fhir.r4.model.DateTimeType(date));
                    }
                }

                bundle.addEntry().setResource(allergy).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("AllergyIntolerance");
                al1Index++;
            } catch (Exception e) {
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

    private void processTelecom(Terser terser, String baseFieldName, Patient patient,
            ContactPoint.ContactPointUse use) {
        int telIndex = 0;
        while (true) {
            try {
                String val = terser.get(baseFieldName + "(" + telIndex + ")-1");
                String equip = terser.get(baseFieldName + "(" + telIndex + ")-2");
                String email = terser.get(baseFieldName + "(" + telIndex + ")-4");

                if ((val == null || val.isEmpty()) && (email == null || email.isEmpty()))
                    break;

                ContactPoint cp = patient.addTelecom();
                if (email != null && !email.isEmpty()
                        && (MappingConstants.EQUIP_INTERNET.equalsIgnoreCase(equip) || email.contains("@"))) {
                    cp.setValue(email);
                    cp.setSystem(ContactPoint.ContactPointSystem.EMAIL);
                } else if (val != null && !val.isEmpty()) {
                    cp.setValue(val);
                    if (MappingConstants.EQUIP_FAX.equalsIgnoreCase(equip))
                        cp.setSystem(ContactPoint.ContactPointSystem.FAX);
                    else
                        cp.setSystem(ContactPoint.ContactPointSystem.PHONE);
                }

                if (MappingConstants.EQUIP_CELL.equalsIgnoreCase(equip)) {
                    cp.setUse(ContactPoint.ContactPointUse.MOBILE);
                } else {
                    cp.setUse(use);
                }

                if (equip != null) {
                    cp.addExtension().setUrl(MappingConstants.EXT_HL7_EQUIPMENT_TYPE).setValue(new StringType(equip));
                }

                telIndex++;
            } catch (Exception e) {
                break;
            }
        }
    }

    private void processParticipants(Terser terser, Encounter encounter) {
        // PV1-7 Attending Doctor (Repeating)
        mapDoctorRep(terser, "/.PV1-7", "ATND", "attender", encounter);
        // PV1-8 Referring Doctor (Repeating)
        mapDoctorRep(terser, "/.PV1-8", "REFR", "referrer", encounter);
        // PV1-9 Consulting Doctor (Repeating)
        mapDoctorRep(terser, "/.PV1-9", "CON", "consultant", encounter);
    }

    private void mapDoctorRep(Terser terser, String baseField, String roleCode, String roleDisplay,
            Encounter encounter) {
        int index = 0;
        while (true) {
            try {
                String path = baseField + "(" + index + ")";
                String docId = terser.get(path + "-1");
                String docFamily = terser.get(path + "-2");
                if (docId == null && docFamily == null)
                    break;

                Encounter.EncounterParticipantComponent participant = encounter.addParticipant();
                participant.addType().addCoding()
                        .setSystem(MappingConstants.SYSTEM_V2_PARTICIPATION_TYPE).setCode(roleCode)
                        .setDisplay(roleDisplay);

                HumanName docName = new HumanName();
                if (docFamily != null)
                    docName.setFamily(docFamily);
                String docGiven = terser.get(path + "-3");
                if (docGiven != null)
                    docName.addGiven(docGiven);

                Reference docRef = new Reference();
                if (docId != null)
                    docRef.setReference("Practitioner/" + docId);
                docRef.setDisplay(docName.isEmpty() ? "Unknown Doctor" : docName.getNameAsSingleString());
                participant.setIndividual(docRef);

                index++;
            } catch (Exception e) {
                break;
            }
        }
    }

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
}
