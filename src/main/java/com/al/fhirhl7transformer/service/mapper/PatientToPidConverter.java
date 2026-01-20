package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.util.MappingConstants;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;

@Component
public class PatientToPidConverter implements FhirToHl7Converter<Patient> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Patient;
    }

    @Override
    public void convert(Patient patient, Message message, Terser terser) throws HL7Exception {
        // PID-1 Set ID
        // Note: Often 1 for single patient messages, handled by caller or fixed here if
        // we assume 1 patient per message
        terser.set("PID-1", "1");

        PID pid = (PID) terser.getSegment("PID");

        // PID-3 Patient Identifiers (Repeating)
        if (patient.hasIdentifier()) {
            List<Identifier> identifiers = new java.util.ArrayList<>(patient.getIdentifier());
            // Filter and put MRN/official first
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
            for (Identifier identifier : identifiers) {
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

        // PID-16 Marital Status
        if (patient.hasMaritalStatus()) {
            pid.getMaritalStatus().getIdentifier()
                    .setValue(patient.getMaritalStatus().getCodingFirstRep().getCode());
        }

        // Extensions (Race, Ethnicity, Religion)
        for (Extension ext : patient.getExtension()) {
            if (ext.getUrl().contains("us-core-race")) {
                Extension omb = ext.getExtensionByUrl("ombCategory");
                if (omb != null && omb.hasValue() && omb.getValue() instanceof Coding) {
                    Coding c = (Coding) omb.getValue();
                    terser.set("/.PID-10-1", c.getCode());
                    terser.set("/.PID-10-2", c.getDisplay());
                }
            } else if (ext.getUrl().contains("us-core-ethnicity")) {
                Extension omb = ext.getExtensionByUrl("ombCategory");
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
            for (Address address : patient.getAddress()) {
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

        // Telecoms (PID-13/14)
        if (patient.hasTelecom()) {
            int homeIdx = 0;
            int workIdx = 0;
            for (ContactPoint cp : patient.getTelecom()) {
                boolean isWork = ContactPoint.ContactPointUse.WORK.equals(cp.getUse());
                boolean isEmail = ContactPoint.ContactPointSystem.EMAIL.equals(cp.getSystem());

                String equipType = MappingConstants.EQUIP_PHONE;
                if (ContactPoint.ContactPointSystem.FAX.equals(cp.getSystem()))
                    equipType = MappingConstants.EQUIP_FAX;
                else if (ContactPoint.ContactPointUse.MOBILE.equals(cp.getUse()))
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

        // PD1-4 Primary Care Provider
        if (patient.hasGeneralPractitioner()) {
            org.hl7.fhir.r4.model.Reference gpr = patient.getGeneralPractitionerFirstRep();
            String pcpName = gpr.getDisplay();
            String pcpId = gpr.getReference();
            if (pcpId != null && pcpId.contains("/"))
                pcpId = pcpId.substring(pcpId.lastIndexOf("/") + 1);

            if (pcpId != null)
                terser.set("/.PD1-4-1", pcpId);
            if (pcpName != null)
                terser.set("/.PD1-4-2", pcpName);
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
    }
}
