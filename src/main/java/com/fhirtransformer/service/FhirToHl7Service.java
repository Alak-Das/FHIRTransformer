package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
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
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.Date;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FhirToHl7Service {

    private final HapiContext hl7Context;
    private final FhirContext fhirContext;
    private final MeterRegistry meterRegistry;

    @Autowired
    public FhirToHl7Service(FhirContext fhirContext, HapiContext hapiContext, MeterRegistry meterRegistry) {
        this.hl7Context = hapiContext;
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
            Terser terser = new Terser(adt);

            // Debug log message structure
            log.debug("DEBUG: HL7 Message Structure: {}", adt.getName());
            for (String name : adt.getNames()) {
                log.debug("DEBUG: Segment/Group name: {}", name);
            }

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
            log.info("DEBUG: Found Patient: {}, Encounter: {}", (patient != null), (encounter != null));

            if (patient != null) {
                PID pid = adt.getPID();
                terser.set("PID-1", "1");
                // PID-3 Patient Identifiers (Repeating)
                if (patient.hasIdentifier()) {
                    // Filter and put MRN/official first
                    java.util.List<org.hl7.fhir.r4.model.Identifier> identifiers = new java.util.ArrayList<>(
                            patient.getIdentifier());
                    identifiers.sort((a, b) -> {
                        boolean aOfficial = (a.hasUse() && "official".equals(a.getUse().toCode()))
                                || (a.hasType() && a.getType().hasCoding()
                                        && "MR".equals(a.getType().getCodingFirstRep().getCode()));
                        boolean bOfficial = (b.hasUse() && "official".equals(b.getUse().toCode()))
                                || (b.hasType() && b.getType().hasCoding()
                                        && "MR".equals(b.getType().getCodingFirstRep().getCode()));
                        if (aOfficial && !bOfficial)
                            return -1;
                        if (!aOfficial && bOfficial)
                            return 1;
                        return 0;
                    });

                    int idIdx = 0;
                    for (org.hl7.fhir.r4.model.Identifier identifier : identifiers) {
                        if (identifier.hasValue()) {
                            pid.getPatientIdentifierList(idIdx).getIDNumber().setValue(identifier.getValue());
                            if (identifier.hasSystem()) {
                                String system = identifier.getSystem();
                                if (system.startsWith("urn:oid:")) {
                                    pid.getPatientIdentifierList(idIdx).getAssigningAuthority().getNamespaceID()
                                            .setValue(system.substring(8));
                                } else {
                                    pid.getPatientIdentifierList(idIdx).getAssigningAuthority().getNamespaceID()
                                            .setValue(system);
                                }
                            }
                            if (identifier.hasType() && identifier.getType().hasCoding()) {
                                pid.getPatientIdentifierList(idIdx).getIdentifierTypeCode()
                                        .setValue(identifier.getType().getCodingFirstRep().getCode());
                            }
                            idIdx++;
                        }
                    }
                }

                // PID-5 Patient Name (Repeating)
                if (patient.hasName()) {
                    int nameIdx = 0;
                    for (HumanName name : patient.getName()) {
                        if (name.hasFamily())
                            pid.getPatientName(nameIdx).getFamilyName().getSurname().setValue(name.getFamily());
                        if (name.hasGiven())
                            pid.getPatientName(nameIdx).getGivenName().setValue(name.getGivenAsSingleString());
                        if (name.hasPrefix())
                            pid.getPatientName(nameIdx).getPrefixEgDR().setValue(name.getPrefixAsSingleString());
                        if (name.hasSuffix())
                            pid.getPatientName(nameIdx).getSuffixEgJRorIII().setValue(name.getSuffixAsSingleString());
                        if (name.hasUse())
                            pid.getPatientName(nameIdx).getNameTypeCode().setValue(name.getUse().toCode());
                        nameIdx++;
                    }
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
                        case OTHER:
                            pid.getAdministrativeSex().setValue("O");
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

                // PID-11 Address (Repeating)
                // PID-16 Marital Status
                if (patient.hasMaritalStatus()) {
                    pid.getMaritalStatus().getIdentifier()
                            .setValue(patient.getMaritalStatus().getCodingFirstRep().getCode());
                }

                // Extensions (Race, Ethnicity, Religion)
                for (org.hl7.fhir.r4.model.Extension ext : patient.getExtension()) {
                    if (ext.getUrl().contains("us-core-race")) {
                        org.hl7.fhir.r4.model.Extension omb = ext.getExtensionByUrl("ombCategory");
                        if (omb != null && omb.hasValue() && omb.getValue() instanceof Coding) {
                            Coding c = (Coding) omb.getValue();
                            terser.set("/.PID-10-1", c.getCode());
                            terser.set("/.PID-10-2", c.getDisplay());
                        }
                    } else if (ext.getUrl().contains("us-core-ethnicity")) {
                        org.hl7.fhir.r4.model.Extension omb = ext.getExtensionByUrl("ombCategory");
                        if (omb != null && omb.hasValue() && omb.getValue() instanceof Coding) {
                            Coding c = (Coding) omb.getValue();
                            terser.set("/.PID-22-1", c.getCode());
                            terser.set("/.PID-22-2", c.getDisplay());
                        }
                    } else if (ext.getUrl().contains("patient-religion")) {
                        if (ext.hasValue() && ext.getValue() instanceof CodeableConcept) {
                            CodeableConcept cc = (CodeableConcept) ext.getValue();
                            terser.set("/.PID-17-1", cc.getCodingFirstRep().getCode());
                            terser.set("/.PID-17-2", cc.getCodingFirstRep().getDisplay());
                        }
                    }
                }

                // PID-29/30 Death Indicator
                if (patient.hasDeceased()) {
                    if (patient.hasDeceasedBooleanType() && patient.getDeceasedBooleanType().getValue()) {
                        terser.set("/.PID-29", "Y");
                    } else if (patient.hasDeceasedDateTimeType()) {
                        terser.set("/.PID-29", "Y");
                        terser.set("/.PID-30", new SimpleDateFormat("yyyyMMddHHmm")
                                .format(patient.getDeceasedDateTimeType().getValue()));
                    }
                }

                // PID-11 Address (Repeating)
                if (patient.hasAddress()) {

                    int addrIdx = 0;
                    for (org.hl7.fhir.r4.model.Address address : patient.getAddress()) {
                        if (address.hasLine()) {
                            pid.getPatientAddress(addrIdx).getStreetAddress().getStreetOrMailingAddress()
                                    .setValue(address.getLine().get(0).getValue());
                            if (address.getLine().size() > 1) {
                                pid.getPatientAddress(addrIdx).getOtherDesignation()
                                        .setValue(address.getLine().get(1).getValue());
                            }
                        }
                        if (address.hasCity())
                            pid.getPatientAddress(addrIdx).getCity().setValue(address.getCity());
                        if (address.hasState())
                            pid.getPatientAddress(addrIdx).getStateOrProvince().setValue(address.getState());
                        if (address.hasPostalCode())
                            pid.getPatientAddress(addrIdx).getZipOrPostalCode().setValue(address.getPostalCode());
                        if (address.hasCountry())
                            pid.getPatientAddress(addrIdx).getCountry().setValue(address.getCountry());
                        if (address.hasUse()) {
                            String use = address.getUse().toCode();
                            if ("home".equals(use))
                                pid.getPatientAddress(addrIdx).getAddressType().setValue("H");
                            else if ("work".equals(use))
                                pid.getPatientAddress(addrIdx).getAddressType().setValue("O");
                        }
                        addrIdx++;
                    }
                }

                if (patient.hasTelecom()) {
                    int homeIdx = 0;
                    int workIdx = 0;
                    for (org.hl7.fhir.r4.model.ContactPoint cp : patient.getTelecom()) {
                        boolean isWork = org.hl7.fhir.r4.model.ContactPoint.ContactPointUse.WORK.equals(cp.getUse());
                        boolean isEmail = org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.EMAIL
                                .equals(cp.getSystem());

                        String equipType = MappingConstants.EQUIP_PHONE;
                        if (org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem.FAX.equals(cp.getSystem()))
                            equipType = MappingConstants.EQUIP_FAX;
                        else if (org.hl7.fhir.r4.model.ContactPoint.ContactPointUse.MOBILE.equals(cp.getUse()))
                            equipType = MappingConstants.EQUIP_CELL;
                        else if (isEmail)
                            equipType = MappingConstants.EQUIP_INTERNET;

                        String useCode = isWork ? "WPN" : "PRN";

                        if (isWork) {
                            if (isEmail)
                                terser.set("PID-14(" + workIdx + ")-4", cp.getValue());
                            else {
                                terser.set("PID-14(" + workIdx + ")-1", cp.getValue());
                                terser.set("PID-14(" + workIdx + ")-2", useCode);
                                terser.set("PID-14(" + workIdx + ")-3", equipType);
                            }
                            workIdx++;
                        } else {
                            if (isEmail)
                                terser.set("PID-13(" + homeIdx + ")-4", cp.getValue());
                            else {
                                terser.set("PID-13(" + homeIdx + ")-1", cp.getValue());
                                terser.set("PID-13(" + homeIdx + ")-2", useCode);
                                terser.set("PID-13(" + homeIdx + ")-3", equipType);
                            }
                            homeIdx++;
                        }
                    }
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
                // ZPI-1 Support (Custom Extension passthrough)
                // NOTE: Z-segments are not part of the standard ADT_A01 structure
                // Attempting to use Terser to set them causes "End of message reached" errors
                // This would require defining a custom message structure extending ADT_A01
                /*
                 * int zIdx = 1;
                 * for (org.hl7.fhir.r4.model.Extension ext : patient.getExtension()) {
                 * if (ext.getUrl().equals(MappingConstants.EXT_HL7_Z_SEGMENT)) {
                 * terser.set("/.ZPI-1(" + (zIdx - 1) + ")", ext.getValue().toString());
                 * zIdx++;
                 * }
                 * }
                 */
            }

            if (encounter != null) {
                PV1 pv1 = adt.getPV1();
                terser.set("PV1-1", "1");
                // PV1-19 Visit Number
                if (encounter.hasIdentifier()) {
                    pv1.getVisitNumber().getIDNumber().setValue(encounter.getIdentifierFirstRep().getValue());
                }

                // PV1-2 Patient Class
                if (encounter.hasClass_()) {
                    pv1.getPatientClass().setValue(encounter.getClass_().getCode());
                }

                // PV1-3 Assigned Patient Location
                if (encounter.hasLocation()) {
                    String loc = encounter.getLocationFirstRep().getLocation().getDisplay();
                    if (loc != null) {
                        pv1.getAssignedPatientLocation().getPointOfCare().setValue(loc);
                    }
                }

                // PV1-4 Admission Type
                if (encounter.hasType() && !encounter.getType().isEmpty()) {
                    pv1.getAdmissionType().setValue(encounter.getType().get(0).getCodingFirstRep().getCode());
                }

                // PD1-4 Primary Care Provider
                if (patient != null && patient.hasGeneralPractitioner()) {
                    Reference gpr = patient.getGeneralPractitionerFirstRep();
                    String pcpName = gpr.getDisplay();
                    String pcpId = gpr.getReference();
                    if (pcpId != null && pcpId.contains("/"))
                        pcpId = pcpId.substring(pcpId.lastIndexOf("/") + 1);

                    if (pcpId != null)
                        terser.set("/.PD1-4-1", pcpId);
                    if (pcpName != null)
                        terser.set("/.PD1-4-2", pcpName);
                }

                // PV1-7/8/9 doctors
                if (encounter.hasParticipant()) {
                    int attendIdx = 0;
                    int referIdx = 0;
                    int consultIdx = 0;
                    for (Encounter.EncounterParticipantComponent participant : encounter.getParticipant()) {
                        if (participant.hasType() && participant.getTypeFirstRep().hasCoding()) {
                            String type = participant.getTypeFirstRep().getCodingFirstRep().getCode();
                            String docName = participant.hasIndividual() && participant.getIndividual().hasDisplay()
                                    ? participant.getIndividual().getDisplay()
                                    : "Unknown Doc";

                            if ("ATND".equals(type)) {
                                pv1.getAttendingDoctor(attendIdx++).getFamilyName().getSurname().setValue(docName);
                            } else if ("REFR".equals(type)) {
                                pv1.getReferringDoctor(referIdx++).getFamilyName().getSurname().setValue(docName);
                            } else if ("CON".equals(type)) {
                                pv1.getConsultingDoctor(consultIdx++).getFamilyName().getSurname().setValue(docName);
                            }
                        }
                    }
                }

                // PV1-10 Hospital Service
                if (encounter.hasServiceType()) {
                    pv1.getHospitalService().setValue(encounter.getServiceType().getCodingFirstRep().getCode());
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

                // Ensure PV2 exists and populate it
                ca.uhn.hl7v2.model.v25.segment.PV2 pv2 = adt.getPV2();

                // PV2-3 Admit Reason
                if (encounter.hasReasonCode()) {
                    CodeableConcept reasonCode = encounter.getReasonCodeFirstRep();
                    String text = reasonCode.hasText() ? reasonCode.getText() : null;
                    Coding reason = reasonCode.getCodingFirstRep();
                    String val = (reason != null && reason.hasCode()) ? reason.getCode() : text;
                    if (val != null) {
                        pv2.getAdmitReason().getIdentifier().setValue(val);
                        pv2.getAdmitReason().getText().setValue(val);
                        // Also set in PV1-18 as a fallback for some legacy systems/tests
                        pv1.getPatientType().setValue(val);
                    }
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
                        if (obs.hasValueQuantity()) {
                            obx.getValueType().setValue("NM"); // Numeric
                            NM nm = new NM(adt);
                            nm.setValue(obs.getValueQuantity().getValue().toString());
                            obx.getObservationValue(0).setData(nm);

                            if (obs.getValueQuantity().hasUnit())
                                obx.getUnits().getIdentifier().setValue(obs.getValueQuantity().getUnit());
                            else if (obs.getValueQuantity().hasCode())
                                obx.getUnits().getIdentifier().setValue(obs.getValueQuantity().getCode());
                        } else if (obs.hasValueStringType()) {
                            obx.getValueType().setValue("ST"); // String
                            ST st = new ST(adt);
                            st.setValue(obs.getValueStringType().getValue());
                            obx.getObservationValue(0).setData(st);
                        } else if (obs.hasValueCodeableConcept()) {
                            obx.getValueType().setValue("CE"); // Coded Element
                            terser.set("/.OBX(" + obxCount + ")-5-1",
                                    obs.getValueCodeableConcept().getCodingFirstRep().getCode());
                            terser.set("/.OBX(" + obxCount + ")-5-2",
                                    obs.getValueCodeableConcept().getCodingFirstRep().getDisplay());
                        }

                        // OBX-3 Observation Identifier
                        if (obs.hasCode() && obs.getCode().hasCoding()) {
                            obx.getObservationIdentifier().getIdentifier()
                                    .setValue(obs.getCode().getCodingFirstRep().getCode());
                            obx.getObservationIdentifier().getText()
                                    .setValue(obs.getCode().getCodingFirstRep().getDisplay());
                            obx.getObservationIdentifier().getNameOfCodingSystem().setValue("LN");
                        }
                    }

                    // OBX-8 Interpretation
                    if (obs.hasInterpretation()) {
                        obx.getAbnormalFlags(0).setValue(obs.getInterpretationFirstRep().getCodingFirstRep().getCode());
                    }

                    // OBX-14 Effective Date/Time
                    if (obs.hasEffectiveDateTimeType()) {
                        obx.getDateTimeOfTheObservation().getTime().setValue(
                                new SimpleDateFormat("yyyyMMddHHmm").format(obs.getEffectiveDateTimeType().getValue()));
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
                            case AMENDED:
                                obx.getObservationResultStatus().setValue("C");
                                break;
                            case ENTEREDINERROR:
                                obx.getObservationResultStatus().setValue("W");
                                break;
                            case CANCELLED:
                                obx.getObservationResultStatus().setValue("X");
                                break;
                            default:
                                obx.getObservationResultStatus().setValue("F");
                                break;
                        }
                    }

                    // OBX-14 Effective Date/Time
                    if (obs.hasEffectiveDateTimeType()) {
                        obx.getDateTimeOfTheObservation().getTime().setValue(
                                new SimpleDateFormat("yyyyMMddHHmm").format(obs.getEffectiveDateTimeType().getValue()));
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

                    // IN1-36 Policy Number / Subscriber ID
                    if (coverage.hasSubscriberId()) {
                        terser.set(in1Path + "-36", coverage.getSubscriberId());
                    }

                    // IN1-2 Insurance Plan ID
                    if (coverage.hasIdentifier()) {
                        terser.set(in1Path + "-2-1", coverage.getIdentifierFirstRep().getValue());
                    }

                    // IN1-3 Company ID / Payor
                    if (coverage.hasPayor()) {
                        org.hl7.fhir.r4.model.Reference payor = coverage.getPayorFirstRep();
                        if (payor.hasReference()) {
                            String id = payor.getReference();
                            if (id.contains("/"))
                                id = id.substring(id.lastIndexOf("/") + 1);
                            terser.set(in1Path + "-3-1", id);
                        }
                        if (payor.hasDisplay()) {
                            terser.set(in1Path + "-4-1", payor.getDisplay());
                        }
                    }

                    // IN1-12/13 Effective Dates
                    if (coverage.hasPeriod()) {
                        if (coverage.getPeriod().hasStart()) {
                            terser.set(in1Path + "-12",
                                    new SimpleDateFormat("yyyyMMdd").format(coverage.getPeriod().getStart()));
                        }
                        if (coverage.getPeriod().hasEnd()) {
                            terser.set(in1Path + "-13",
                                    new SimpleDateFormat("yyyyMMdd").format(coverage.getPeriod().getEnd()));
                        }
                    }

                    // IN1-17 Relationship to Insured
                    if (coverage.hasRelationship()) {
                        terser.set(in1Path + "-17-1", coverage.getRelationship().getCodingFirstRep().getCode());
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

                    // GT1-8 DOB
                    if (rp.hasBirthDate()) {
                        terser.set(gt1Path + "-8", new SimpleDateFormat("yyyyMMdd").format(rp.getBirthDate()));
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
