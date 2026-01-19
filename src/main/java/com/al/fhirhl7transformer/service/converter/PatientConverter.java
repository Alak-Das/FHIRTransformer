package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.util.DateTimeUtil;
import com.al.fhirhl7transformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class PatientConverter implements SegmentConverter<Patient> {

    @Override
    public List<Patient> convert(Terser terser, Bundle bundle, ConversionContext context) {
        try {
            log.debug("Processing Patient segment...");

            String mainPathToUse = "/.PID";
            boolean found = false;

            // Try root path first
            try {
                if (terser.getSegment(mainPathToUse) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Try PATIENT group path (often used in OML/ORU)
                String patientPath = "/.PATIENT/PID";
                try {
                    if (terser.getSegment(patientPath) != null) {
                        mainPathToUse = patientPath;
                        found = true;
                    }
                } catch (Exception ex) {
                    // Not found
                }
            }

            if (!found) {
                log.warn("PID segment not found at root or standard groups");
                return Collections.emptyList();
            }

            Patient patient = new Patient();
            if (context.getPatientId() != null) {
                patient.setId(context.getPatientId());
            }

            // PID-3 Patient Identifiers (Repeating)
            int idIndex = 0;
            while (idIndex < 10) { // Safety limit
                String pid3_1 = terser.get(mainPathToUse + "-3(" + idIndex + ")-1");
                if (pid3_1 == null)
                    break;
                String pid3_4 = terser.get(mainPathToUse + "-3(" + idIndex + ")-4"); // Assigning Authority
                String pid3_5 = terser.get(mainPathToUse + "-3(" + idIndex + ")-5"); // Identifier Type Code

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
            while (nameIndex < 10) { // Safety limit
                String familyName = terser.get(mainPathToUse + "-5(" + nameIndex + ")-1");
                String givenName = terser.get(mainPathToUse + "-5(" + nameIndex + ")-2");
                if (familyName == null && givenName == null)
                    break;

                HumanName name = patient.addName().setFamily(familyName);
                if (givenName != null)
                    name.addGiven(givenName);

                String middleName = terser.get(mainPathToUse + "-5(" + nameIndex + ")-3");
                if (middleName != null)
                    name.addGiven(middleName);

                String suffix = terser.get(mainPathToUse + "-5(" + nameIndex + ")-4");
                if (suffix != null)
                    name.addSuffix(suffix);

                String prefix = terser.get(mainPathToUse + "-5(" + nameIndex + ")-5");
                if (prefix != null)
                    name.addPrefix(prefix);

                String nameType = terser.get(mainPathToUse + "-5(" + nameIndex + ")-7");
                if (nameType != null) {
                    try {
                        name.setUse(HumanName.NameUse.fromCode(nameType.toLowerCase()));
                    } catch (Exception e) {
                        // ignore invalid code
                    }
                }
                nameIndex++;
            }

            // PID-8 Gender
            String gender = terser.get(mainPathToUse + "-8");
            if ("M".equalsIgnoreCase(gender)) {
                patient.setGender(Enumerations.AdministrativeGender.MALE);
            } else if ("F".equalsIgnoreCase(gender)) {
                patient.setGender(Enumerations.AdministrativeGender.FEMALE);
            } else if ("O".equalsIgnoreCase(gender)) {
                patient.setGender(Enumerations.AdministrativeGender.OTHER);
            } else {
                patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
            }

            // PID-7 DOB
            String dob = terser.get(mainPathToUse + "-7");
            if (dob != null && !dob.isEmpty()) {
                patient.setBirthDate(java.sql.Date.valueOf(DateTimeUtil.parseHl7Date(dob)));
            }

            // PID-10 Race (Repeating)
            String race = terser.get(mainPathToUse + "-10(0)-1");
            String raceText = terser.get(mainPathToUse + "-10(0)-2");
            if (race != null) {
                Extension raceExt = new Extension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race");
                raceExt.addExtension(new Extension("ombCategory",
                        new Coding("http://terminology.hl7.org/CodeSystem/v3-Race", race, raceText)));
                raceExt.addExtension(new Extension("text", new StringType(raceText != null ? raceText : "Unknown")));
                patient.addExtension(raceExt);
            }

            // PID-16 Marital Status
            String maritalStatus = terser.get(mainPathToUse + "-16-1");
            if (maritalStatus != null) {
                patient.getMaritalStatus().addCoding().setSystem(MappingConstants.SYSTEM_V2_MARITAL_STATUS)
                        .setCode(maritalStatus).setDisplay(terser.get(mainPathToUse + "-16-2"));
            }

            // PID-17 Religion
            if (terser.get(mainPathToUse + "-17-1") != null) {
                patient.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/patient-religion")
                        .setValue(new CodeableConcept().addCoding().setSystem(MappingConstants.SYSTEM_RELIGION)
                                .setCode(terser.get(mainPathToUse + "-17-1"))
                                .setDisplay(terser.get(mainPathToUse + "-17-2")));
            }

            // PID-22 Ethnic Group
            String ethnicityCode = terser.get(mainPathToUse + "-22-1");
            String ethnicityText = terser.get(mainPathToUse + "-22-2");

            if (ethnicityCode != null) {
                if (ethnicityText == null) {
                    ethnicityText = "Unknown";
                }
                patient.addExtension().setUrl("http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity")
                        .addExtension(new Extension().setUrl("ombCategory")
                                .setValue(
                                        new Coding().setSystem("urn:oid:2.16.840.1.113883.6.238").setCode(ethnicityCode)
                                                .setDisplay(ethnicityText)))
                        .addExtension(new Extension().setUrl("text").setValue(new StringType(ethnicityText)));
            }

            // PID-29/30 Death Details
            String deceased = terser.get(mainPathToUse + "-29");
            if ("Y".equalsIgnoreCase(deceased)) {
                patient.setDeceased(new BooleanType(true));
                String deathDate = terser.get(mainPathToUse + "-30");
                if (deathDate != null && !deathDate.isEmpty()) {
                    patient.setDeceased(new DateTimeType(
                            java.util.Date.from(DateTimeUtil.parseHl7DateTime(deathDate).toInstant())));
                }
            }

            // PD1-4 Primary Care Provider
            try {
                // PD1 is usually companion to PID, same level
                String pd1Path = mainPathToUse.replace("PID", "PD1");
                String pcpId = terser.get(pd1Path + "-4-1");
                String pcpName = terser.get(pd1Path + "-4-2");
                if (pcpId != null || pcpName != null) {
                    Reference gp = patient.addGeneralPractitioner();
                    if (pcpId != null)
                        gp.setReference("Practitioner/" + pcpId);
                    if (pcpName != null)
                        gp.setDisplay(pcpName);
                    else if (pcpId != null && pcpId.length() > 5)
                        gp.setDisplay(pcpId);
                }
            } catch (Exception e) {
                log.debug("Could not process PD1 segment: {}", e.getMessage());
            }

            // PID-11 Addresses (Repeating)
            int addrIndex = 0;
            while (addrIndex < 10) { // Safety limit
                String street = terser.get(mainPathToUse + "-11(" + addrIndex + ")-1");
                String city = terser.get(mainPathToUse + "-11(" + addrIndex + ")-3");
                if (street == null && city == null)
                    break;

                Address address = patient.addAddress();
                if (street != null)
                    address.addLine(street);
                String otherLine = terser.get(mainPathToUse + "-11(" + addrIndex + ")-2");
                if (otherLine != null)
                    address.addLine(otherLine);

                address.setCity(city);
                address.setState(terser.get(mainPathToUse + "-11(" + addrIndex + ")-4"));
                address.setPostalCode(terser.get(mainPathToUse + "-11(" + addrIndex + ")-5"));
                address.setCountry(terser.get(mainPathToUse + "-11(" + addrIndex + ")-6"));

                String type = terser.get(mainPathToUse + "-11(" + addrIndex + ")-7");
                if (type != null) {
                    if ("H".equals(type))
                        address.setUse(Address.AddressUse.HOME);
                    else if ("O".equals(type) || "B".equals(type))
                        address.setUse(Address.AddressUse.WORK);
                }
                addrIndex++;
            }

            // PID-13/14 Telecom
            processTelecom(terser, mainPathToUse + "-13", patient, ContactPoint.ContactPointUse.HOME);
            processTelecom(terser, mainPathToUse + "-14", patient, ContactPoint.ContactPointUse.WORK);

            // NK1 Next of Kin (Contacts)
            int nk1Index = 0;
            while (nk1Index < 10) { // Safety limit
                try {
                    String nk1Path = mainPathToUse.replace("PID", "NK1") + "(" + nk1Index + ")";
                    String lastName = terser.get(nk1Path + "-2-1");
                    if (lastName == null)
                        break;

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
                    nk1Index++;
                } catch (Exception e) {
                    break;
                }
            }

            // Z-Segment Processing - Use generic message handling as terser path might vary
            if (context.getHapiMessage() != null) {
                processZSegments(terser, context.getHapiMessage(), patient);
            }

            return Collections.singletonList(patient);

        } catch (

        Exception e) {
            log.error("Error converting Patient segment", e);
            throw new RuntimeException("Patient conversion failed", e);
        }
    }

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

    private void processZSegments(Terser terser, Message hapiMsg, Patient patient) {
        log.debug("Processing Z-Segments...");

        // 1. Process specific ZPI Segment (Custom Patient Info)
        try {
            // Check for ZPI fields
            String setID = terser.get("/.ZPI-1");
            String petName = terser.get("/.ZPI-2");
            String vipLevel = terser.get("/.ZPI-3");
            String archiveStatus = terser.get("/.ZPI-4");

            if (petName != null || vipLevel != null || archiveStatus != null) {
                log.info("Found ZPI Segment (SetID={}): Pet='{}', VIP='{}', Archive='{}'", setID, petName, vipLevel,
                        archiveStatus);

                if (petName != null && !petName.isEmpty()) {
                    patient.addExtension()
                            .setUrl("http://example.org/fhir/StructureDefinition/pet-name")
                            .setValue(new StringType(petName));
                }

                if (vipLevel != null && !vipLevel.isEmpty()) {
                    patient.addExtension()
                            .setUrl("http://example.org/fhir/StructureDefinition/vip-level")
                            .setValue(new StringType(vipLevel));
                }

                if (archiveStatus != null && !archiveStatus.isEmpty()) {
                    patient.addExtension()
                            .setUrl("http://example.org/fhir/StructureDefinition/archive-status")
                            .setValue(new StringType(archiveStatus));
                }
            }
        } catch (Exception e) {
            log.debug("ZPI segment not found or parse error: {}", e.getMessage());
        }

        // 2. Preserve other Z-segments as raw extensions
        try {
            for (String groupName : hapiMsg.getNames()) {
                if (groupName.startsWith("Z") && !groupName.equals("ZPI")) {
                    try {
                        ca.uhn.hl7v2.model.Structure struct = hapiMsg.get(groupName);
                        if (struct instanceof Segment) {
                            Segment seg = (Segment) struct;
                            patient.addExtension()
                                    .setUrl(MappingConstants.EXT_HL7_Z_SEGMENT)
                                    .setValue(new StringType(seg.encode()));
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error looking up generic Z-segments: {}", e.getMessage());
        }
    }
}
