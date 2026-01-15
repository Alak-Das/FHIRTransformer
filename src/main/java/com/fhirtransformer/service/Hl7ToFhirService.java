package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.config.TenantContext;
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

            // Map Encounter Data
            processEncounter(terser, bundle, patientId);

            // Map OBX segments (Observations)
            processObservations(terser, bundle, patientId);

            // Map DG1 segments (Conditions)
            processConditions(terser, bundle, patientId);

            // Map AL1 segments (Allergies)
            processAllergies(terser, bundle, patientId);

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
            patient.addIdentifier().setValue(pid3).setSystem("urn:oid:2.16.840.1.113883.2.1.4.1");
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
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode(patientClass));
                }

                String docId = terser.get("/.PV1-7-1");
                String docFamily = terser.get("/.PV1-7-2");
                String docGiven = terser.get("/.PV1-7-3");
                if (docId != null || docFamily != null) {
                    Encounter.EncounterParticipantComponent participant = encounter.addParticipant();
                    participant.addType().addCoding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").setCode("ATND")
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
                if (admitDate != null && !admitDate.isEmpty()) {
                    try {
                        Date date = new SimpleDateFormat("yyyyMMddHHmm").parse(admitDate.substring(0, 12));
                        Period period = new Period();
                        period.setStart(date);
                        encounter.setPeriod(period);
                    } catch (Exception e) {
                        try {
                            Date date = new SimpleDateFormat("yyyyMMdd").parse(admitDate.substring(0, 8));
                            Period period = new Period();
                            period.setStart(date);
                            encounter.setPeriod(period);
                        } catch (Exception ex) {
                        }
                    }
                }

                bundle.addEntry().setResource(encounter).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("Encounter");
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
                code.addCoding().setSystem("http://loinc.org").setCode(obx3).setDisplay(obx3Text);
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
                if ("F".equals(status)) {
                    observation.setStatus(Observation.ObservationStatus.FINAL);
                } else if ("P".equals(status)) {
                    observation.setStatus(Observation.ObservationStatus.PRELIMINARY);
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
                        .setSystem("http://terminology.hl7.org/CodeSystem/condition-ver-status").setCode("confirmed")));
                condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical").setCode("active")));

                String diagnosisName = terser.get(dg1Path + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem("http://hl7.org/fhir/sid/icd-10").setCode(diagnosisCode)
                        .setDisplay(diagnosisName);
                condition.setCode(code);

                String type = terser.get(dg1Path + "-6");
                if (type != null && !type.isEmpty()) {
                    CodeableConcept category = new CodeableConcept();
                    category.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/condition-category")
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
                        new Coding().setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-verification")
                                .setCode("confirmed")));
                allergy.setClinicalStatus(new CodeableConcept().addCoding(
                        new Coding().setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical")
                                .setCode("active")));

                // AL1-2 Allergy Type (DA=Drug Allergy, FA=Food Allergy, etc.)
                String type = terser.get(al1Path + "-2");
                if (type != null) {
                    if ("DA".equals(type) || "MA".equals(type)) {
                        allergy.addCategory(
                                AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
                    } else if ("FA".equals(type)) {
                        allergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
                    } else if ("EA".equals(type)) {
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

                // AL1-5 Reaction
                String reaction = terser.get(al1Path + "-5");
                if (reaction != null && !reaction.isEmpty()) {
                    allergy.addReaction().addManifestation(new CodeableConcept().setText(reaction));
                }

                bundle.addEntry().setResource(allergy).getRequest().setMethod(Bundle.HTTPVerb.POST)
                        .setUrl("AllergyIntolerance");
                al1Index++;
            } catch (Exception e) {
                break;
            }
        }
    }
}
