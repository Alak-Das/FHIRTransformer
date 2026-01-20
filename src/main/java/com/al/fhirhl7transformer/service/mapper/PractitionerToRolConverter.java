package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR Practitioner to HL7 ROL (Role) segment.
 * Also populates practitioner info in other segments like OBX-16, ORC-12,
 * PV1-7/8/9.
 */
@Component
public class PractitionerToRolConverter implements FhirToHl7Converter<Practitioner> {

    private int rolIndex = 0;
    private final SimpleDateFormat hl7DateFormat = new SimpleDateFormat("yyyyMMdd");

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Practitioner;
    }

    @Override
    public void convert(Practitioner practitioner, Message message, Terser terser) throws HL7Exception {
        String rolPath = "/.ROL(" + rolIndex + ")";

        // ROL-1 Role Instance ID
        if (practitioner.hasIdentifier()) {
            Identifier id = practitioner.getIdentifierFirstRep();
            if (id.hasValue()) {
                terser.set(rolPath + "-1-1", id.getValue());
            }
            if (id.hasSystem()) {
                terser.set(rolPath + "-1-4", id.getSystem());
            }
        } else if (practitioner.hasId()) {
            terser.set(rolPath + "-1-1", practitioner.getIdElement().getIdPart());
        }

        // ROL-2 Action Code
        terser.set(rolPath + "-2", "AD"); // Add

        // ROL-3 Role-ROL (Role code)
        // Derive from practitioner qualification or meta tag
        String roleCode = "PP"; // Default: Primary Physician
        if (practitioner.hasMeta() && practitioner.getMeta().hasTag()) {
            for (Coding tag : practitioner.getMeta().getTag()) {
                if (tag.hasCode()) {
                    String code = tag.getCode().toUpperCase();
                    if ("ATTENDING".equals(code) || "AT".equals(code)) {
                        roleCode = "AT";
                    } else if ("REFERRING".equals(code) || "RP".equals(code)) {
                        roleCode = "RP";
                    } else if ("CONSULTING".equals(code) || "CP".equals(code)) {
                        roleCode = "CP";
                    } else if ("ADMITTING".equals(code) || "AD".equals(code)) {
                        roleCode = "AD";
                    }
                }
            }
        }
        terser.set(rolPath + "-3-1", roleCode);

        // ROL-4 Role Person (XCN - Extended Composite ID Number and Name for Persons)
        // XCN.1 - ID Number
        if (practitioner.hasIdentifier()) {
            terser.set(rolPath + "-4-1", practitioner.getIdentifierFirstRep().getValue());
        }

        // XCN.2-7 - Name components
        if (practitioner.hasName()) {
            HumanName name = practitioner.getNameFirstRep();
            if (name.hasFamily()) {
                terser.set(rolPath + "-4-2", name.getFamily());
            }
            if (name.hasGiven()) {
                terser.set(rolPath + "-4-3", name.getGivenAsSingleString());
            }
            if (name.hasPrefix()) {
                terser.set(rolPath + "-4-6", name.getPrefixAsSingleString());
            }
            if (name.hasSuffix()) {
                terser.set(rolPath + "-4-5", name.getSuffixAsSingleString());
            }
        }

        // XCN.9 - Assigning Authority
        if (practitioner.hasIdentifier()) {
            Identifier id = practitioner.getIdentifierFirstRep();
            if (id.hasSystem()) {
                terser.set(rolPath + "-4-9-1", id.getSystem());
            }
        }

        // ROL-5 Role Begin Date/Time
        // ROL-6 Role End Date/Time
        if (practitioner.hasMeta() && practitioner.getMeta().hasLastUpdated()) {
            terser.set(rolPath + "-5", hl7DateFormat.format(practitioner.getMeta().getLastUpdated()));
        }

        // ROL-7 Role Duration
        // (Not directly available in Practitioner)

        // ROL-8 Role Action Reason
        // (Not directly available in Practitioner)

        // ROL-9 Provider Type
        if (practitioner.hasQualification()) {
            Practitioner.PractitionerQualificationComponent qual = practitioner.getQualificationFirstRep();
            if (qual.hasCode() && qual.getCode().hasCoding()) {
                Coding coding = qual.getCode().getCodingFirstRep();
                terser.set(rolPath + "-9-1", coding.getCode());
                if (coding.hasDisplay()) {
                    terser.set(rolPath + "-9-2", coding.getDisplay());
                }
            }
        }

        // ROL-10 Organization Unit Type
        // (Would come from PractitionerRole if available)

        // ROL-11 Office/Home Address/Birthplace
        if (practitioner.hasAddress()) {
            Address addr = practitioner.getAddressFirstRep();
            if (addr.hasLine()) {
                terser.set(rolPath + "-11-1", addr.getLine().get(0).getValue());
            }
            if (addr.hasCity()) {
                terser.set(rolPath + "-11-3", addr.getCity());
            }
            if (addr.hasState()) {
                terser.set(rolPath + "-11-4", addr.getState());
            }
            if (addr.hasPostalCode()) {
                terser.set(rolPath + "-11-5", addr.getPostalCode());
            }
            if (addr.hasCountry()) {
                terser.set(rolPath + "-11-6", addr.getCountry());
            }
        }

        // ROL-12 Phone
        if (practitioner.hasTelecom()) {
            int phoneIdx = 0;
            for (ContactPoint telecom : practitioner.getTelecom()) {
                if (telecom.hasValue()) {
                    String phonePath = rolPath + "-12(" + phoneIdx + ")";
                    terser.set(phonePath + "-1", telecom.getValue());

                    // Set telecommunication use code
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

                    // Set telecommunication equipment type
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

        // Also try to populate PV1 attending/referring/consulting if applicable
        try {
            if ("AT".equals(roleCode) && practitioner.hasName()) {
                terser.set("PV1-7-1", practitioner.getIdentifierFirstRep().getValue());
                terser.set("PV1-7-2", practitioner.getNameFirstRep().getFamily());
                terser.set("PV1-7-3", practitioner.getNameFirstRep().getGivenAsSingleString());
            } else if ("RP".equals(roleCode) && practitioner.hasName()) {
                terser.set("PV1-8-1", practitioner.getIdentifierFirstRep().getValue());
                terser.set("PV1-8-2", practitioner.getNameFirstRep().getFamily());
                terser.set("PV1-8-3", practitioner.getNameFirstRep().getGivenAsSingleString());
            } else if ("CP".equals(roleCode) && practitioner.hasName()) {
                terser.set("PV1-9-1", practitioner.getIdentifierFirstRep().getValue());
                terser.set("PV1-9-2", practitioner.getNameFirstRep().getFamily());
                terser.set("PV1-9-3", practitioner.getNameFirstRep().getGivenAsSingleString());
            }
        } catch (Exception e) {
            // PV1 segment may not exist - ignore
        }

        rolIndex++;
    }
}
