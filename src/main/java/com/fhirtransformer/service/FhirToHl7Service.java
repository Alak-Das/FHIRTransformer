package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.PV1;
import ca.uhn.hl7v2.model.v25.segment.OBX;
import ca.uhn.hl7v2.model.v25.segment.DG1;
import ca.uhn.hl7v2.model.v25.datatype.NM;
import ca.uhn.hl7v2.model.v25.datatype.ST;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.util.MappingConstants;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.RelatedPerson;
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

                // PID-29 & PID-30 Death Details
                if (patient.hasDeceasedBooleanType() && patient.getDeceasedBooleanType().getValue()) {
                    pid.getPatientDeathIndicator().setValue("Y");
                }
                if (patient.hasDeceasedDateTimeType()) {
                    pid.getPatientDeathIndicator().setValue("Y");
                    pid.getPatientDeathDateAndTime().getTime().setValue(
                            new SimpleDateFormat("yyyyMMddHHmm").format(patient.getDeceasedDateTimeType().getValue()));
                }

                // Map Next of Kin (NK1)
                // Use Terser for NK1 as it is a repeating segment group
                Terser terser = new Terser(adt);
                if (patient.hasContact()) {
                    int nk1Count = 0;
                    for (Patient.ContactComponent contact : patient.getContact()) {
                        String nk1Path = "/.NK1(" + nk1Count + ")";

                        // NK1-1 Set ID
                        terser.set(nk1Path + "-1", String.valueOf(nk1Count + 1));

                        // NK1-2 Name
                        if (contact.hasName()) {
                            HumanName name = contact.getName();
                            if (name.hasFamily())
                                terser.set(nk1Path + "-2-1", name.getFamily());
                            if (name.hasGiven())
                                terser.set(nk1Path + "-2-2", name.getGivenAsSingleString());
                        }

                        // NK1-3 Relationship
                        if (contact.hasRelationship()) {
                            Coding coding = contact.getRelationshipFirstRep().getCodingFirstRep();
                            if (coding.hasCode())
                                terser.set(nk1Path + "-3-1", coding.getCode());
                            if (coding.hasDisplay())
                                terser.set(nk1Path + "-3-2", coding.getDisplay());
                        }

                        // NK1-4 Address
                        if (contact.hasAddress()) {
                            Address addr = contact.getAddress();
                            if (addr.hasLine())
                                terser.set(nk1Path + "-4-1", addr.getLine().get(0).getValue());
                            if (addr.hasCity())
                                terser.set(nk1Path + "-4-3", addr.getCity());
                            if (addr.hasState())
                                terser.set(nk1Path + "-4-4", addr.getState());
                            if (addr.hasPostalCode())
                                terser.set(nk1Path + "-4-5", addr.getPostalCode());
                        }

                        // NK1-5 Phone
                        if (contact.hasTelecom()) {
                            terser.set(nk1Path + "-5-1", contact.getTelecomFirstRep().getValue());
                        }

                        nk1Count++;
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

                // PV1-45 Discharge Date
                if (encounter.hasPeriod() && encounter.getPeriod().hasEnd()) {
                    pv1.getDischargeDateTime(0).getTime()
                            .setValue(new SimpleDateFormat("yyyyMMddHHmm").format(encounter.getPeriod().getEnd()));
                }

                // PV2-3 Admit Reason
                Terser terser = new Terser(adt);
                if (encounter.hasReasonCode()) {
                    Coding reason = encounter.getReasonCodeFirstRep().getCodingFirstRep();
                    if (reason.hasCode())
                        terser.set("/.PV2-3-1", reason.getCode());
                    if (reason.hasDisplay())
                        terser.set("/.PV2-3-2", reason.getDisplay());
                }
            }

            // Map Observations to OBX
            int obxCount = 0;
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource().getResourceType() == ResourceType.Observation) {
                    Observation obs = (Observation) entry.getResource();
                    // We need to retrieve the OBX repetition. HAPI V2 models usually have a method
                    // to get specific repetition.
                    // For typical ADT/ORU structures, OBX is repeating.
                    // However, we are using ADT_A01 which technically allows OBX after other
                    // segments in some versions, or as part of a group?
                    // ADT_A01 in v2.5 has OBX.

                    OBX obx = adt.getOBX(obxCount);

                    // OBX-1 Set ID
                    obx.getSetIDOBX().setValue(String.valueOf(obxCount + 1));

                    // Check if it is a Note (LOINC 34109-9)
                    boolean isNote = obs.hasCode() && obs.getCode().hasCoding() &&
                            "34109-9".equals(obs.getCode().getCodingFirstRep().getCode());

                    if (isNote) {
                        obx.getValueType().setValue("TX"); // Text
                        obx.getObservationIdentifier().getIdentifier().setValue("34109-9");
                        obx.getObservationIdentifier().getText().setValue("Note");
                        if (obs.hasValueStringType()) {
                            obx.getObservationValue(0).getData().parse(obs.getValueStringType().getValue());
                        }
                    } else {
                        // Standard OBX Mapping

                        // OBX-2 Value Type & Value
                        if (obs.hasValueQuantity()) {
                            obx.getValueType().setValue("NM"); // Numeric

                            NM nm = new NM(adt);
                            nm.setValue(obs.getValueQuantity().getValue().toString());
                            obx.getObservationValue(0).setData(nm);

                            obx.getUnits().getIdentifier().setValue(obs.getValueQuantity().getUnit());
                        } else if (obs.hasValueStringType()) {
                            obx.getValueType().setValue("ST"); // String
                            ST st = new ST(adt);
                            st.setValue(obs.getValueStringType().getValue());
                            obx.getObservationValue(0).setData(st);
                        }

                        // OBX-3 Observation Identifier
                        if (obs.hasCode() && obs.getCode().hasCoding()) {
                            obx.getObservationIdentifier().getIdentifier()
                                    .setValue(obs.getCode().getCodingFirstRep().getCode());
                            obx.getObservationIdentifier().getText()
                                    .setValue(obs.getCode().getCodingFirstRep().getDisplay());
                        }
                    }

                    // OBX-11 Status
                    if (obs.hasStatus()) {
                        switch (obs.getStatus()) {
                            case FINAL:
                                obx.getObservationResultStatus().setValue("F");
                                break;
                            case PRELIMINARY:
                                obx.getObservationResultStatus().setValue("P");
                                break;
                            default:
                                obx.getObservationResultStatus().setValue("C");
                                break; // Corrected/Other
                        }
                    }

                    obxCount++;
                }
            }

            // Map Conditions to DG1
            int dg1Count = 0;
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource().getResourceType() == ResourceType.Condition) {
                    Condition cond = (Condition) entry.getResource();

                    DG1 dg1 = adt.getDG1(dg1Count);

                    // DG1-1 Set ID
                    dg1.getSetIDDG1().setValue(String.valueOf(dg1Count + 1));

                    // DG1-3 Diagnosis Code
                    if (cond.hasCode() && cond.getCode().hasCoding()) {
                        dg1.getDiagnosisCodeDG1().getIdentifier()
                                .setValue(cond.getCode().getCodingFirstRep().getCode());
                        dg1.getDiagnosisCodeDG1().getText().setValue(cond.getCode().getCodingFirstRep().getDisplay());
                        dg1.getDiagnosisCodeDG1().getNameOfCodingSystem().setValue("ICD-10");
                    }

                    // DG1-6 Diagnosis Type
                    if (cond.hasCategory()) {
                        dg1.getDiagnosisType().setValue(cond.getCategoryFirstRep().getCodingFirstRep().getDisplay());
                    }

                    dg1Count++;
                }
            }

            // Map AllergyIntolerance to AL1
            int al1Count = 0;
            Terser terser = new Terser(adt);
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource().getResourceType() == ResourceType.AllergyIntolerance) {
                    AllergyIntolerance allergy = (AllergyIntolerance) entry.getResource();

                    String al1Path = "/.AL1(" + al1Count + ")";

                    // AL1-1 Set ID
                    terser.set(al1Path + "-1", String.valueOf(al1Count + 1));

                    // AL1-2 Allergen Type Code
                    if (allergy.hasCategory() && !allergy.getCategory().isEmpty()) {
                        String cat = allergy.getCategory().get(0).getValue().toCode();
                        if ("medication".equalsIgnoreCase(cat))
                            terser.set(al1Path + "-2", MappingConstants.ALLERGY_TYPE_DRUG); // Drug
                        else if ("food".equalsIgnoreCase(cat))
                            terser.set(al1Path + "-2", MappingConstants.ALLERGY_TYPE_FOOD); // Food
                        else
                            terser.set(al1Path + "-2", MappingConstants.ALLERGY_TYPE_ENV); // Environmental/Other
                    }

                    // AL1-3 Allergen Code/Mnemonic/Description
                    if (allergy.hasCode() && allergy.getCode().hasCoding()) {
                        terser.set(al1Path + "-3-1", allergy.getCode().getCodingFirstRep().getCode());
                        terser.set(al1Path + "-3-2", allergy.getCode().getCodingFirstRep().getDisplay());
                    }

                    // AL1-5 Allergy Reaction
                    if (allergy.hasReaction()) {
                        // Take first reaction manifestation
                        if (!allergy.getReactionFirstRep().getManifestation().isEmpty()) {
                            terser.set(al1Path + "-5",
                                    allergy.getReactionFirstRep().getManifestationFirstRep().getText());
                        }
                    }

                    al1Count++;
                }
            }

            // Map Coverage to IN1
            int in1Count = 0;
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource().getResourceType() == ResourceType.Coverage) {
                    Coverage coverage = (Coverage) entry.getResource();
                    String in1Path = "/.IN1(" + in1Count + ")";

                    // IN1-1 Set ID
                    terser.set(in1Path + "-1", String.valueOf(in1Count + 1));

                    // IN1-36 Policy Number
                    if (coverage.hasSubscriberId()) {
                        terser.set(in1Path + "-36", coverage.getSubscriberId());
                    }

                    // IN1-3 Company ID / Payor
                    if (!coverage.getPayor().isEmpty()) {
                        org.hl7.fhir.r4.model.Reference payor = coverage.getPayorFirstRep();
                        if (payor.hasReference()) {
                            // strip "Organization/" if present
                            String id = payor.getReference();
                            if (id.contains("/"))
                                id = id.substring(id.indexOf("/") + 1);
                            terser.set(in1Path + "-3-1", id);
                        }
                        if (payor.hasDisplay()) {
                            terser.set(in1Path + "-4-1", payor.getDisplay());
                        }
                    }

                    // IN1-47 Plan Type
                    if (coverage.hasType() && coverage.getType().hasCoding()) {
                        terser.set(in1Path + "-47", coverage.getType().getCodingFirstRep().getCode());
                    }

                    in1Count++;
                }
            }

            // Map Procedure to PR1
            int pr1Count = 0;
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource().getResourceType() == ResourceType.Procedure) {
                    Procedure procedure = (Procedure) entry.getResource();
                    String pr1Path = "/.PR1(" + pr1Count + ")";

                    // PR1-1 Set ID
                    terser.set(pr1Path + "-1", String.valueOf(pr1Count + 1));

                    // PR1-3 Procedure Code
                    if (procedure.hasCode() && procedure.getCode().hasCoding()) {
                        terser.set(pr1Path + "-3-1", procedure.getCode().getCodingFirstRep().getCode());
                        terser.set(pr1Path + "-3-2", procedure.getCode().getCodingFirstRep().getDisplay());
                    }

                    // PR1-5 Procedure Date/Time
                    if (procedure.hasPerformedDateTimeType()) {
                        terser.set(pr1Path + "-5",
                                new SimpleDateFormat("yyyyMMddHHmm")
                                        .format(procedure.getPerformedDateTimeType().getValue()));
                    }

                    pr1Count++;
                }
            }

            // Map RelatedPerson to GT1
            int gt1Count = 0;
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource().getResourceType() == ResourceType.RelatedPerson) {
                    RelatedPerson rp = (RelatedPerson) entry.getResource();
                    String gt1Path = "/.GT1(" + gt1Count + ")";

                    // GT1-1 Set ID
                    terser.set(gt1Path + "-1", String.valueOf(gt1Count + 1));

                    // GT1-3 Name
                    if (rp.hasName()) {
                        HumanName name = rp.getNameFirstRep();
                        if (name.hasFamily())
                            terser.set(gt1Path + "-3-1", name.getFamily());
                        if (name.hasGiven())
                            terser.set(gt1Path + "-3-2", name.getGivenAsSingleString());
                    }

                    // GT1-5 Address
                    if (rp.hasAddress()) {
                        Address addr = rp.getAddressFirstRep();
                        if (addr.hasLine())
                            terser.set(gt1Path + "-5-1", addr.getLine().get(0).getValue());
                        if (addr.hasCity())
                            terser.set(gt1Path + "-5-3", addr.getCity());
                        if (addr.hasState())
                            terser.set(gt1Path + "-5-4", addr.getState());
                        if (addr.hasPostalCode())
                            terser.set(gt1Path + "-5-5", addr.getPostalCode());
                    }

                    // GT1-6 Phone
                    if (rp.hasTelecom()) {
                        terser.set(gt1Path + "-6-1", rp.getTelecomFirstRep().getValue());
                    }

                    // GT1-11 Relationship
                    if (rp.hasRelationship()) {
                        Coding code = rp.getRelationshipFirstRep().getCodingFirstRep();
                        if (code.hasCode())
                            terser.set(gt1Path + "-11-1", code.getCode());
                        if (code.hasDisplay())
                            terser.set(gt1Path + "-11-2", code.getDisplay());
                    }

                    gt1Count++;
                }
            }

            // Serialize to Pipe Delimited
            Parser parser = hl7Context.getPipeParser();
            String result = parser.encode(adt);

            // Record Success Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "fhir-to-v2", "status", "success").increment();
            sample.stop(meterRegistry.timer("fhir.conversion.time", "type", "fhir-to-v2"));

            return result;

        } catch (

        Exception e) {
            // Record Failure Metrics
            meterRegistry.counter("fhir.conversion.count", "type", "fhir-to-v2", "status", "error").increment();
            throw e;
        }
    }
}
