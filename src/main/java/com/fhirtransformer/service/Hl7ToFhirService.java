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
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
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

            // Extract Patient Data (Basic mapping from PID segment)
            // Note: Real-world mapping would be much more extensive
            Patient patient = new Patient();
            String patientId = UUID.randomUUID().toString();
            patient.setId(patientId);

            // Map Patient Identifiers (PID-3)
            // Simplistic approach: getting the first repetition
            String pid3 = terser.get("/.PID-3-1");
            if (pid3 != null && !pid3.isEmpty()) {
                patient.addIdentifier().setValue(pid3).setSystem("urn:oid:2.16.840.1.113883.2.1.4.1"); // Example System
            }

            // Map Name (PID-5)
            String familyName = terser.get("/.PID-5-1");
            String givenName = terser.get("/.PID-5-2");
            if (familyName != null || givenName != null) {
                patient.addName().setFamily(familyName).addGiven(givenName);
            }

            // Map Gender (PID-8)
            String gender = terser.get("/.PID-8");
            if ("M".equals(gender)) {
                patient.setGender(Enumerations.AdministrativeGender.MALE);
            } else if ("F".equals(gender)) {
                patient.setGender(Enumerations.AdministrativeGender.FEMALE);
            } else {
                patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
            }

            // Map Date of Birth (PID-7)
            String dob = terser.get("/.PID-7");
            if (dob != null && !dob.isEmpty()) {
                try {
                    // HL7 dates can be YYYYMMDD or long timestamps. We parse simpler ones for now.
                    Date dateOffset = new SimpleDateFormat("yyyyMMdd").parse(dob.substring(0, 8));
                    patient.setBirthDate(dateOffset);
                } catch (Exception e) {
                    // Ignore parse errors
                }
            }

            // Map Address (PID-11)
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

            // Map Phone (PID-13)
            String phone = terser.get("/.PID-13-1");
            if (phone != null && !phone.isEmpty()) {
                patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(phone);
            }

            // Map Marital Status (PID-16)
            String marital = terser.get("/.PID-16");
            if (marital != null && !marital.isEmpty()) {
                CodeableConcept maritalStatus = new CodeableConcept();
                maritalStatus.addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus")
                        .setCode(marital);
                patient.setMaritalStatus(maritalStatus);
            }

            // Add Patient to Bundle
            bundle.addEntry()
                    .setResource(patient)
                    .getRequest()
                    .setMethod(Bundle.HTTPVerb.POST)
                    .setUrl("Patient");

            // Map Encounter Data if PV1 exists
            // We need to check if it's an ADT message or similar that has PV1
            // For simplicity, we try to access it via Terser, if it throws or is empty we
            // skip
            try {
                String checkPv1 = terser.get("/.PV1-1"); // View if PV1 exists
                if (checkPv1 != null) {
                    Encounter encounter = new Encounter();
                    encounter.setId(UUID.randomUUID().toString());
                    encounter.setStatus(Encounter.EncounterStatus.FINISHED); // Defaulting
                    encounter.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/" + patientId));

                    // Visit Number
                    String visitNum = terser.get("/.PV1-19");
                    if (visitNum != null) {
                        encounter.addIdentifier().setValue(visitNum);
                    }

                    // Class
                    String patientClass = terser.get("/.PV1-2");
                    if (patientClass != null) {
                        encounter.setClass_(new org.hl7.fhir.r4.model.Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                                .setCode(patientClass));
                    }

                    // Attending Doctor (PV1-7)
                    String docId = terser.get("/.PV1-7-1");
                    String docFamily = terser.get("/.PV1-7-2");
                    String docGiven = terser.get("/.PV1-7-3");
                    if (docId != null || docFamily != null) {
                        Encounter.EncounterParticipantComponent participant = encounter.addParticipant();
                        participant.addType().addCoding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType")
                                .setCode("ATND")
                                .setDisplay("attender");

                        HumanName docName = new HumanName();
                        if (docFamily != null)
                            docName.setFamily(docFamily);
                        if (docGiven != null)
                            docName.addGiven(docGiven);

                        participant.setIndividual(
                                new org.hl7.fhir.r4.model.Reference().setDisplay(docName.getNameAsSingleString()));
                    }

                    // Admit Date (PV1-44)
                    String admitDate = terser.get("/.PV1-44");
                    if (admitDate != null && !admitDate.isEmpty()) {
                        try {
                            Date date = new SimpleDateFormat("yyyyMMddHHmm").parse(admitDate.substring(0, 12));
                            Period period = new Period();
                            period.setStart(date);
                            encounter.setPeriod(period);
                        } catch (Exception e) {
                            // try shorter format
                            try {
                                Date date = new SimpleDateFormat("yyyyMMdd").parse(admitDate.substring(0, 8));
                                Period period = new Period();
                                period.setStart(date);
                                encounter.setPeriod(period);
                            } catch (Exception ex) {
                                // ignore
                            }
                        }
                    }

                    bundle.addEntry()
                            .setResource(encounter)
                            .getRequest()
                            .setMethod(Bundle.HTTPVerb.POST)
                            .setUrl("Encounter");
                }
            } catch (Exception e) {
                // PV1 segment might not exist or be accessible cleanly
                // Clean handling would be checking message structure
            }

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
}
