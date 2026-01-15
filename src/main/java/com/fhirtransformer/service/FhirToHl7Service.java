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

import java.text.SimpleDateFormat;
import java.util.Date;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class FhirToHl7Service {

    private final HapiContext hl7Context;
    private final FhirContext fhirContext;
    private final MeterRegistry meterRegistry;

    @Autowired
    public FhirToHl7Service(FhirContext fhirContext, MeterRegistry meterRegistry) {
        this.hl7Context = new DefaultHapiContext();
        this.fhirContext = fhirContext;
        this.meterRegistry = meterRegistry;
    }

    public String convertFhirToHl7(String fhirJson) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
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

            // Use Bundle ID as MSH-10 (Preserve Transaction ID)
            if (bundle.hasId()) {
                msh.getMessageControlID().setValue(bundle.getIdElement().getIdPart());
            }

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

                // PID-7 Date of Birth
                if (patient.hasBirthDate()) {
                    pid.getDateTimeOfBirth().getTime()
                            .setValue(new SimpleDateFormat("yyyyMMdd").format(patient.getBirthDate()));
                }

                // PID-11 Address
                if (patient.hasAddress()) {
                    org.hl7.fhir.r4.model.Address address = patient.getAddressFirstRep();
                    if (address.hasLine()) {
                        pid.getPatientAddress(0).getStreetAddress().getStreetOrMailingAddress()
                                .setValue(address.getLine().get(0).getValue());
                    }
                    if (address.hasCity()) {
                        pid.getPatientAddress(0).getCity().setValue(address.getCity());
                    }
                    if (address.hasState()) {
                        pid.getPatientAddress(0).getStateOrProvince().setValue(address.getState());
                    }
                    if (address.hasPostalCode()) {
                        pid.getPatientAddress(0).getZipOrPostalCode().setValue(address.getPostalCode());
                    }
                }

                // PID-13 Phone
                if (patient.hasTelecom()) {
                    pid.getPhoneNumberHome(0).getTelephoneNumber().setValue(patient.getTelecomFirstRep().getValue());
                }

                // PID-16 Marital Status
                if (patient.hasMaritalStatus()) {
                    pid.getMaritalStatus().getIdentifier()
                            .setValue(patient.getMaritalStatus().getCodingFirstRep().getCode());
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

                // PV1-7 Attending Doctor
                if (encounter.hasParticipant()) {
                    for (Encounter.EncounterParticipantComponent participant : encounter.getParticipant()) {
                        // Check if ATND
                        if (participant.hasType() && participant.getTypeFirstRep().hasCoding() &&
                                "ATND".equals(participant.getTypeFirstRep().getCodingFirstRep().getCode())) {
                            if (participant.hasIndividual() && participant.getIndividual().hasDisplay()) {
                                // Simplified: Put entire display name in Family Name
                                pv1.getAttendingDoctor(0).getFamilyName().getSurname()
                                        .setValue(participant.getIndividual().getDisplay());
                            }
                            break;
                        }
                    }
                }

                // PV1-44 Admit Date
                if (encounter.hasPeriod() && encounter.getPeriod().hasStart()) {
                    pv1.getAdmitDateTime().getTime()
                            .setValue(new SimpleDateFormat("yyyyMMddHHmm").format(encounter.getPeriod().getStart()));
                }
            }

            // Serialize to Pipe Delimited
            Parser parser = hl7Context.getPipeParser();
            String result = parser.encode(adt);

            // Record Success Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "fhir-to-v2", "status", "success").increment();
            sample.stop(meterRegistry.timer("fhir.conversion.time", "type", "fhir-to-v2"));

            return result;

        } catch (Exception e) {
            // Record Failure Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "fhir-to-v2", "status", "error").increment();
            throw e;
        }
    }
}
