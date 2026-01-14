package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.config.TenantContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Encounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class Hl7ToFhirService {

    private final HapiContext hl7Context;
    private final FhirContext fhirContext;
    private final FhirValidationService fhirValidationService;

    @Autowired
    public Hl7ToFhirService(FhirValidationService fhirValidationService, FhirContext fhirContext) {
        this.hl7Context = new DefaultHapiContext();
        this.fhirContext = fhirContext;
        this.fhirValidationService = fhirValidationService;
    }

    public String convertHl7ToFhir(String hl7Message) throws Exception {
        // Parse HL7 Message
        Parser p = hl7Context.getPipeParser();
        Message hapiMsg = p.parse(hl7Message);
        Terser terser = new Terser(hapiMsg);

        // Create FHIR Bundle
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.setId(UUID.randomUUID().toString());

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
        return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }
}
