package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class Hl7ToFhirService {

    private final HapiContext hl7Context;
    private final FhirContext fhirContext;
    private final FhirValidationService fhirValidationService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public Hl7ToFhirService(FhirValidationService fhirValidationService, FhirContext fhirContext,
            MeterRegistry meterRegistry) {
        this.hl7Context = new DefaultHapiContext();
        this.hl7Context.setValidationContext(new ca.uhn.hl7v2.validation.impl.NoValidation());
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

            // Extract Patient Data
            String patientId = processPatient(terser, bundle);

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

            // Validate the Bundle
            fhirValidationService.validateAndThrow(bundle);

            // Serialize to JSON
            String result = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);

            // Record Success Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "v2-to-fhir", "status", "success").increment();
            sample.stop(meterRegistry.timer("fhir.conversion.time", "type", "v2-to-fhir"));

            return result;

        } catch (Exception e) {
            // Record Failure Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "v2-to-fhir", "status", "error").increment();
            throw e;
        }
    }

    private String processPatient(Terser terser, Bundle bundle) throws Exception {
        Patient patient = new Patient();
        String patientId = UUID.randomUUID().toString();
        patient.setId(patientId);

        String pid3 = terser.get("/.PID-3-1");
        if (pid3 != null && !pid3.isEmpty()) {
            patient.addIdentifier().setValue(pid3).setSystem(MappingConstants.SYSTEM_PATIENT_IDENTIFIER);
        }

        String familyName = terser.get("/.PID-5-1");
        String givenName = terser.get("/.PID-5-2");
        if (familyName != null || givenName != null) {
            patient.addName().setFamily(familyName).addGiven(givenName);
        }

        String gender = terser.get("/.PID-8");
        if ("M".equals(gender)) {
            patient.setGender(Enumerations.AdministrativeGender.MALE);
        } else if ("F".equals(gender)) {
            patient.setGender(Enumerations.AdministrativeGender.FEMALE);
        } else {
            patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
        }

        String dob = terser.get("/.PID-7");
        if (dob != null && !dob.isEmpty()) {
            try {
                Date dateOffset = new SimpleDateFormat("yyyyMMdd").parse(dob.substring(0, 8));
                patient.setBirthDate(dateOffset);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        String street = terser.get("/.PID-11-1");
        String city = terser.get("/.PID-11-3");
        String state = terser.get("/.PID-11-4");
        String zip = terser.get("/.PID-11-5");
        if (street != null || city != null || state != null || zip != null) {
            Address address = patient.addAddress();
            if (street != null)
                address.addLine(street);
            if (city != null)
                address.setCity(city);
            if (state != null)
                address.setState(state);
            if (zip != null)
                address.setPostalCode(zip);
        }

        String phone = terser.get("/.PID-13-1");
        if (phone != null && !phone.isEmpty()) {
            patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(phone);
        }

        String marital = terser.get("/.PID-16");
        if (marital != null && !marital.isEmpty()) {
            CodeableConcept maritalStatus = new CodeableConcept();
            maritalStatus.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus")
                    .setCode(marital);
            patient.setMaritalStatus(maritalStatus);
        }

        // PID-30 Patient Death Indicator
        String deathInd = terser.get("/.PID-30");
        if ("Y".equals(deathInd)) {
            patient.setDeceased(new org.hl7.fhir.r4.model.BooleanType(true));
        }

        // PID-29 Patient Death Date
        String deathDate = terser.get("/.PID-29");
        if (deathDate != null && !deathDate.isEmpty()) {
            try {
                Date date = new SimpleDateFormat("yyyyMMddHHmm").parse(deathDate.substring(0, 12));
                patient.setDeceased(new org.hl7.fhir.r4.model.DateTimeType(date));
            } catch (Exception e) {
                try {
                    Date date = new SimpleDateFormat("yyyyMMdd").parse(deathDate.substring(0, 8));
                    patient.setDeceased(new org.hl7.fhir.r4.model.DateTimeType(date));
                } catch (Exception ex) {
                }
            }
        }

        bundle.addEntry().setResource(patient).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");
        return patientId;
    }

    private void processEncounter(Terser terser, Bundle bundle, String patientId) {
        try {
            String checkPv1 = terser.get("/.PV1-1");
            if (checkPv1 != null) {
                Encounter encounter = new Encounter();
                encounter.setId(UUID.randomUUID().toString());
                encounter.setStatus(Encounter.EncounterStatus.FINISHED);
                encounter.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));

                String visitNum = terser.get("/.PV1-19");
                if (visitNum != null) {
                    encounter.addIdentifier().setValue(visitNum);
                }

                String patientClass = terser.get("/.PV1-2");
                if (patientClass != null) {
                    encounter.setClass_(new org.hl7.fhir.r4.model.Coding()
                            .setSystem(MappingConstants.SYSTEM_V2_ACT_CODE).setCode(patientClass));
                }

                String docId = terser.get("/.PV1-7-1");
                String docFamily = terser.get("/.PV1-7-2");
                String docGiven = terser.get("/.PV1-7-3");
                if (docId != null || docFamily != null) {
                    Encounter.EncounterParticipantComponent participant = encounter.addParticipant();
                    participant.addType().addCoding()
                            .setSystem(MappingConstants.SYSTEM_V2_PARTICIPATION_TYPE).setCode("ATND")
                            .setDisplay("attender");
                    HumanName docName = new HumanName();
                    if (docFamily != null)
                        docName.setFamily(docFamily);
                    if (docGiven != null)
                        docName.addGiven(docGiven);
                    participant.setIndividual(
                            new org.hl7.fhir.r4.model.Reference().setDisplay(docName.getNameAsSingleString()));
                }

                String admitDate = terser.get("/.PV1-44");
                // specific logic: if PV1-44 empty, try EVN-2 (Recorded Date/Time)
                if (admitDate == null || admitDate.isEmpty()) {
                    admitDate = terser.get("/.EVN-2");
                }

                if (admitDate != null && !admitDate.isEmpty()) {
                    try {
                        Date date;
                        if (admitDate.length() >= 12) {
                            date = new SimpleDateFormat("yyyyMMddHHmm").parse(admitDate.substring(0, 12));
                        } else {
                            date = new SimpleDateFormat("yyyyMMdd").parse(admitDate.substring(0, 8));
                        }
                        Period period = encounter.hasPeriod() ? encounter.getPeriod() : new Period();
                        period.setStart(date);
                        encounter.setPeriod(period);
                    } catch (Exception e) {
                        // ignore invalid date
                    }
                }

                // PV1-45 Discharge Date
                String dischargeDate = terser.get("/.PV1-45");
                if (dischargeDate != null && !dischargeDate.isEmpty()) {
                    try {
                        Date date;
                        if (dischargeDate.length() >= 12) {
                            date = new SimpleDateFormat("yyyyMMddHHmm").parse(dischargeDate.substring(0, 12));
                        } else {
                            date = new SimpleDateFormat("yyyyMMdd").parse(dischargeDate.substring(0, 8));
                        }
                        Period period = encounter.hasPeriod() ? encounter.getPeriod() : new Period();
                        period.setEnd(date);
                        encounter.setPeriod(period);
                    } catch (Exception e) {
                        // ignore
                    }
                }

                bundle.addEntry().setResource(encounter).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Encounter");

                // PV2-3 Admit Reason
                try {
                    String reason = terser.get("/.PV2-3-2"); // Text
                    if (reason != null && !reason.isEmpty()) {
                        encounter.addReasonCode().setText(reason);
                    }
                } catch (Exception ex) {
                    // PV2 might not exist
                }
            }
        } catch (Exception e) {
            // PV1 segment might not exist or be accessible cleanly
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

                // OBX-14 Date/Time of the Observation
                String effectiveDate = terser.get(obxPath + "-14");
                if (effectiveDate != null && !effectiveDate.isEmpty()) {
                    try {
                        Date date;
                        if (effectiveDate.length() >= 12) {
                            date = new SimpleDateFormat("yyyyMMddHHmm").parse(effectiveDate.substring(0, 12));
                        } else {
                            date = new SimpleDateFormat("yyyyMMdd").parse(effectiveDate.substring(0, 8));
                        }
                        observation.setEffective(new org.hl7.fhir.r4.model.DateTimeType(date));
                    } catch (Exception e) {
                        // ignore
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
                String severity = terser.get(al1Path + "-4");
                if (severity != null) {
                    if ("MI".equals(severity))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MILD);
                    else if ("MO".equals(severity))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MODERATE);
                    else if ("SV".equals(severity))
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
                coverage.setSubscriberId(terser.get(in1Path + "-36")); // IN1-36 Policy Number

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

                // Date
                String date = terser.get(pr1Path + "-5");
                if (date != null) {
                    try {
                        procedure.setPerformed(new org.hl7.fhir.r4.model.DateTimeType(
                                new SimpleDateFormat("yyyyMMddHHmm").parse(date)));
                    } catch (Exception e) {
                        // ignore
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
}
