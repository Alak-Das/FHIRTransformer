package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR PractitionerRole to HL7 ROL (Role) segment.
 * Provides more detailed role mapping than the basic Practitioner converter.
 */
@Component
public class PractitionerRoleToRolConverter implements FhirToHl7Converter<PractitionerRole> {

    private int rolIndex = 0;
    private final SimpleDateFormat hl7DateFormat = new SimpleDateFormat("yyyyMMdd");

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof PractitionerRole;
    }

    @Override
    public void convert(PractitionerRole practitionerRole, Message message, Terser terser) throws HL7Exception {
        String rolPath = "/.ROL(" + rolIndex + ")";

        // ROL-1 Role Instance ID
        if (practitionerRole.hasIdentifier()) {
            Identifier id = practitionerRole.getIdentifierFirstRep();
            if (id.hasValue()) {
                terser.set(rolPath + "-1-1", id.getValue());
            }
            if (id.hasSystem()) {
                terser.set(rolPath + "-1-4", id.getSystem());
            }
        } else if (practitionerRole.hasId()) {
            terser.set(rolPath + "-1-1", practitionerRole.getIdElement().getIdPart());
        }

        // ROL-2 Action Code
        terser.set(rolPath + "-2", "AD"); // Add

        // ROL-3 Role-ROL (Role code)
        if (practitionerRole.hasCode()) {
            CodeableConcept code = practitionerRole.getCodeFirstRep();
            if (code.hasCoding()) {
                Coding coding = code.getCodingFirstRep();
                terser.set(rolPath + "-3-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(rolPath + "-3-2", coding.getDisplay());
                }
                if (coding.hasSystem()) {
                    terser.set(rolPath + "-3-3", coding.getSystem());
                }
            }
        } else {
            terser.set(rolPath + "-3-1", "PP"); // Default: Primary Physician
        }

        // ROL-4 Role Person (from practitioner reference)
        if (practitionerRole.hasPractitioner()) {
            Reference practRef = practitionerRole.getPractitioner();
            if (practRef.hasReference()) {
                String ref = practRef.getReference();
                if (ref.contains("/")) {
                    terser.set(rolPath + "-4-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
            if (practRef.hasDisplay()) {
                // Parse display name
                String display = practRef.getDisplay();
                if (display.contains(",")) {
                    String[] parts = display.split(",", 2);
                    terser.set(rolPath + "-4-2", parts[0].trim()); // Family
                    if (parts.length > 1) {
                        terser.set(rolPath + "-4-3", parts[1].trim()); // Given
                    }
                } else if (display.contains(" ")) {
                    String[] parts = display.split(" ", 2);
                    terser.set(rolPath + "-4-3", parts[0].trim()); // Given
                    if (parts.length > 1) {
                        terser.set(rolPath + "-4-2", parts[1].trim()); // Family
                    }
                } else {
                    terser.set(rolPath + "-4-2", display);
                }
            }
        }

        // ROL-5 Role Begin Date/Time
        // ROL-6 Role End Date/Time
        if (practitionerRole.hasPeriod()) {
            Period period = practitionerRole.getPeriod();
            if (period.hasStart()) {
                terser.set(rolPath + "-5", hl7DateFormat.format(period.getStart()));
            }
            if (period.hasEnd()) {
                terser.set(rolPath + "-6", hl7DateFormat.format(period.getEnd()));
            }
        }

        // ROL-9 Provider Type (from specialty)
        if (practitionerRole.hasSpecialty()) {
            CodeableConcept specialty = practitionerRole.getSpecialtyFirstRep();
            if (specialty.hasCoding()) {
                Coding coding = specialty.getCodingFirstRep();
                terser.set(rolPath + "-9-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(rolPath + "-9-2", coding.getDisplay());
                }
            }
        }

        // ROL-10 Organization Unit Type (from organization reference)
        if (practitionerRole.hasOrganization()) {
            Reference orgRef = practitionerRole.getOrganization();
            if (orgRef.hasDisplay()) {
                terser.set(rolPath + "-10-1", orgRef.getDisplay());
            }
        }

        // ROL-11 Office/Home Address (from location)
        if (practitionerRole.hasLocation()) {
            Reference locRef = practitionerRole.getLocationFirstRep();
            if (locRef.hasDisplay()) {
                terser.set(rolPath + "-11-1", locRef.getDisplay());
            }
        }

        // ROL-12 Phone (from telecom)
        if (practitionerRole.hasTelecom()) {
            int phoneIdx = 0;
            for (ContactPoint telecom : practitionerRole.getTelecom()) {
                if (telecom.hasValue()) {
                    String phonePath = rolPath + "-12(" + phoneIdx + ")";
                    terser.set(phonePath + "-1", telecom.getValue());

                    // Set use code
                    if (telecom.hasUse()) {
                        switch (telecom.getUse()) {
                            case WORK:
                                terser.set(phonePath + "-2", "WPN");
                                break;
                            case HOME:
                                terser.set(phonePath + "-2", "PRN");
                                break;
                            case MOBILE:
                                terser.set(phonePath + "-2", "CP");
                                break;
                            default:
                                terser.set(phonePath + "-2", "NET");
                        }
                    }

                    // Set equipment type
                    if (telecom.hasSystem()) {
                        switch (telecom.getSystem()) {
                            case PHONE:
                                terser.set(phonePath + "-3", "PH");
                                break;
                            case FAX:
                                terser.set(phonePath + "-3", "FX");
                                break;
                            case EMAIL:
                                terser.set(phonePath + "-3", "Internet");
                                break;
                            case PAGER:
                                terser.set(phonePath + "-3", "BP");
                                break;
                            default:
                                terser.set(phonePath + "-3", "PH");
                        }
                    }
                    phoneIdx++;
                }
            }
        }

        // ROL-13 Certification (from qualification reference)
        // Not directly available in PractitionerRole

        rolIndex++;
    }
}
