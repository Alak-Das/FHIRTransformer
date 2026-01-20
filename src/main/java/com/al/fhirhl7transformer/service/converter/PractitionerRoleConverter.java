package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Converts HL7 ROL (Role) segment to FHIR PractitionerRole resource.
 * This provides more detailed role information than the Practitioner resource
 * alone.
 */
@Slf4j
@Component
public class PractitionerRoleConverter implements SegmentConverter<PractitionerRole> {

    private final SimpleDateFormat hl7DateFormat = new SimpleDateFormat("yyyyMMdd");
    private final SimpleDateFormat hl7DateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public List<PractitionerRole> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<PractitionerRole> results = new ArrayList<>();
        int rolIndex = 0;

        while (rolIndex < 20) { // Safety limit
            try {
                String rolPath = "/.ROL(" + rolIndex + ")";
                String roleCode = terser.get(rolPath + "-3-1");

                if (roleCode == null || roleCode.isEmpty()) {
                    break;
                }

                PractitionerRole practitionerRole = new PractitionerRole();
                practitionerRole.setId(java.util.UUID.randomUUID().toString());

                // ROL-1 Role Instance ID
                String roleId = terser.get(rolPath + "-1-1");
                if (roleId != null && !roleId.isEmpty()) {
                    practitionerRole.addIdentifier()
                            .setValue(roleId)
                            .setSystem("urn:hl7:role-instance-id");
                }

                // ROL-3 Role-ROL (Role code)
                CodeableConcept code = new CodeableConcept();
                code.addCoding()
                        .setCode(roleCode)
                        .setSystem("http://terminology.hl7.org/CodeSystem/v2-0443")
                        .setDisplay(getRoleDisplay(roleCode));
                practitionerRole.addCode(code);

                // ROL-4 Role Person (Create reference to Practitioner)
                String practId = terser.get(rolPath + "-4-1");
                String familyName = terser.get(rolPath + "-4-2");
                String givenName = terser.get(rolPath + "-4-3");

                if (practId != null || familyName != null) {
                    String display = familyName != null ? familyName : "";
                    if (givenName != null) {
                        display = givenName + " " + display;
                    }
                    practitionerRole.setPractitioner(new Reference()
                            .setReference("Practitioner/"
                                    + (practId != null ? practId : java.util.UUID.randomUUID().toString()))
                            .setDisplay(display.trim()));
                }

                // ROL-5 Role Begin Date/Time
                String beginDate = terser.get(rolPath + "-5");
                // ROL-6 Role End Date/Time
                String endDate = terser.get(rolPath + "-6");

                if ((beginDate != null && !beginDate.isEmpty()) ||
                        (endDate != null && !endDate.isEmpty())) {
                    Period period = new Period();
                    if (beginDate != null && !beginDate.isEmpty()) {
                        try {
                            Date start = beginDate.length() > 8
                                    ? hl7DateTimeFormat.parse(beginDate.substring(0, Math.min(14, beginDate.length())))
                                    : hl7DateFormat.parse(beginDate.substring(0, Math.min(8, beginDate.length())));
                            period.setStart(start);
                        } catch (ParseException e) {
                            log.debug("Could not parse begin date: {}", beginDate);
                        }
                    }
                    if (endDate != null && !endDate.isEmpty()) {
                        try {
                            Date end = endDate.length() > 8
                                    ? hl7DateTimeFormat.parse(endDate.substring(0, Math.min(14, endDate.length())))
                                    : hl7DateFormat.parse(endDate.substring(0, Math.min(8, endDate.length())));
                            period.setEnd(end);
                        } catch (ParseException e) {
                            log.debug("Could not parse end date: {}", endDate);
                        }
                    }
                    practitionerRole.setPeriod(period);
                }

                // ROL-9 Provider Type
                String providerType = terser.get(rolPath + "-9-1");
                String providerTypeDesc = terser.get(rolPath + "-9-2");
                if (providerType != null && !providerType.isEmpty()) {
                    CodeableConcept specialty = new CodeableConcept();
                    specialty.addCoding()
                            .setCode(providerType)
                            .setDisplay(providerTypeDesc);
                    practitionerRole.addSpecialty(specialty);
                }

                // ROL-12 Phone
                for (int phoneIdx = 0; phoneIdx < 5; phoneIdx++) {
                    String phone = terser.get(rolPath + "-12(" + phoneIdx + ")-1");
                    if (phone == null || phone.isEmpty())
                        break;

                    String useCode = terser.get(rolPath + "-12(" + phoneIdx + ")-2");
                    String equipType = terser.get(rolPath + "-12(" + phoneIdx + ")-3");

                    ContactPoint cp = new ContactPoint();
                    cp.setValue(phone);

                    // Map use code
                    if ("WPN".equals(useCode)) {
                        cp.setUse(ContactPoint.ContactPointUse.WORK);
                    } else if ("PRN".equals(useCode)) {
                        cp.setUse(ContactPoint.ContactPointUse.HOME);
                    } else if ("CP".equals(useCode)) {
                        cp.setUse(ContactPoint.ContactPointUse.MOBILE);
                    }

                    // Map equipment type
                    if ("FX".equals(equipType)) {
                        cp.setSystem(ContactPoint.ContactPointSystem.FAX);
                    } else if ("Internet".equals(equipType)) {
                        cp.setSystem(ContactPoint.ContactPointSystem.EMAIL);
                    } else if ("BP".equals(equipType)) {
                        cp.setSystem(ContactPoint.ContactPointSystem.PAGER);
                    } else {
                        cp.setSystem(ContactPoint.ContactPointSystem.PHONE);
                    }

                    practitionerRole.addTelecom(cp);
                }

                // Set active status
                practitionerRole.setActive(true);

                results.add(practitionerRole);
                rolIndex++;
            } catch (Exception e) {
                log.debug("Error processing ROL segment at index {}: {}", rolIndex, e.getMessage());
                break;
            }
        }

        return results.isEmpty() ? Collections.emptyList() : results;
    }

    private String getRoleDisplay(String roleCode) {
        switch (roleCode) {
            case "AD":
                return "Admitting";
            case "AT":
                return "Attending";
            case "CP":
                return "Consulting";
            case "FHCP":
                return "Family Health Care Provider";
            case "PP":
                return "Primary Care Provider";
            case "RP":
                return "Referring";
            case "RT":
                return "Referred to Provider";
            default:
                return roleCode;
        }
    }
}
