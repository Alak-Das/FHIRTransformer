package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.PV1;
import ca.uhn.hl7v2.parser.Parser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

@Service
public class FhirToHl7Service {

    private final HapiContext hl7Context;
    private final FhirContext fhirContext;

    @Autowired
    public FhirToHl7Service(FhirContext fhirContext) {
        this.hl7Context = new DefaultHapiContext();
        this.fhirContext = fhirContext;
    }

    public String convertFhirToHl7(String fhirJson) throws Exception {
        // Parse FHIR JSON
        Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, fhirJson);

        // Create HL7 Message (ADT^A01 as default target for this PoC)
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");

        // Populate MSH
        MSH msh = adt.getMSH();
        msh.getSendingApplication().getNamespaceID().setValue("FHIRTransformer");
        msh.getReceivingApplication().getNamespaceID().setValue("LegacyApp");
        msh.getDateTimeOfMessage().getTime().setValue(new Date());

        // Extract Patient and Encounter from Bundle
        Patient patient = null;
        Encounter encounter = null;

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource().getResourceType() == ResourceType.Patient) {
                patient = (Patient) entry.getResource();
            } else if (entry.getResource().getResourceType() == ResourceType.Encounter) {
                encounter = (Encounter) entry.getResource();
            }
        }

        if (patient != null) {
            PID pid = adt.getPID();
            // PID-3 Patient ID
            if (patient.hasIdentifier()) {
                pid.getPatientIdentifierList(0).getIDNumber().setValue(patient.getIdentifierFirstRep().getValue());
            }

            // PID-5 Name
            if (patient.hasName()) {
                pid.getPatientName(0).getFamilyName().getSurname().setValue(patient.getNameFirstRep().getFamily());
                pid.getPatientName(0).getGivenName().setValue(patient.getNameFirstRep().getGivenAsSingleString());
            }

            // PID-8 Gender
            if (patient.hasGender()) {
                switch (patient.getGender()) {
                    case MALE:
                        pid.getAdministrativeSex().setValue("M");
                        break;
                    case FEMALE:
                        pid.getAdministrativeSex().setValue("F");
                        break;
                    default:
                        pid.getAdministrativeSex().setValue("U");
                        break;
                }
            }
        }

        if (encounter != null) {
            PV1 pv1 = adt.getPV1();
            // PV1-19 Visit Number
            if (encounter.hasIdentifier()) {
                pv1.getVisitNumber().getIDNumber().setValue(encounter.getIdentifierFirstRep().getValue());
            }

            // PV1-2 Patient Class
            if (encounter.hasClass_()) {
                pv1.getPatientClass().setValue(encounter.getClass_().getCode());
            }
        }

        // Serialize to Pipe Delimited
        Parser parser = hl7Context.getPipeParser();
        return parser.encode(adt);
    }
}
