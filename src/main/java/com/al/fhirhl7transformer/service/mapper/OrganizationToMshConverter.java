package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Converts FHIR Organization to HL7 MSH segment fields (MSH-3, MSH-4, MSH-5,
 * MSH-6).
 * Also populates other organization-related fields in various segments.
 */
@Component
public class OrganizationToMshConverter implements FhirToHl7Converter<Organization> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Organization;
    }

    @Override
    public void convert(Organization organization, Message message, Terser terser) throws HL7Exception {
        // Determine if this is a sending or receiving organization based on type/meta
        boolean isSending = true;
        boolean isReceiving = false;

        if (organization.hasType()) {
            for (CodeableConcept type : organization.getType()) {
                if (type.hasCoding()) {
                    String code = type.getCodingFirstRep().getCode();
                    if ("receiver".equalsIgnoreCase(code) || "receiving".equalsIgnoreCase(code)) {
                        isReceiving = true;
                        isSending = false;
                    } else if ("sender".equalsIgnoreCase(code) || "sending".equalsIgnoreCase(code)) {
                        isSending = true;
                    }
                }
            }
        }

        // Check for meta tags
        if (organization.hasMeta() && organization.getMeta().hasTag()) {
            for (Coding tag : organization.getMeta().getTag()) {
                if ("receiver".equalsIgnoreCase(tag.getCode())) {
                    isReceiving = true;
                    isSending = false;
                } else if ("sender".equalsIgnoreCase(tag.getCode())) {
                    isSending = true;
                }
            }
        }

        if (isSending) {
            // MSH-3 Sending Application
            // MSH-4 Sending Facility
            if (organization.hasName()) {
                terser.set("MSH-4-1", organization.getName());
            }

            if (organization.hasIdentifier()) {
                Identifier id = organization.getIdentifierFirstRep();
                if (id.hasValue()) {
                    terser.set("MSH-4-2", id.getValue());
                }
                if (id.hasSystem()) {
                    String system = id.getSystem();
                    if (system.startsWith("urn:oid:")) {
                        terser.set("MSH-4-3", system.substring(8));
                    } else {
                        terser.set("MSH-4-3", system);
                    }
                }
            }

            // Application name from alias if available
            if (organization.hasAlias()) {
                terser.set("MSH-3-1", organization.getAlias().get(0).getValue());
            }
        }

        if (isReceiving) {
            // MSH-5 Receiving Application
            // MSH-6 Receiving Facility
            if (organization.hasName()) {
                terser.set("MSH-6-1", organization.getName());
            }

            if (organization.hasIdentifier()) {
                Identifier id = organization.getIdentifierFirstRep();
                if (id.hasValue()) {
                    terser.set("MSH-6-2", id.getValue());
                }
                if (id.hasSystem()) {
                    String system = id.getSystem();
                    if (system.startsWith("urn:oid:")) {
                        terser.set("MSH-6-3", system.substring(8));
                    } else {
                        terser.set("MSH-6-3", system);
                    }
                }
            }

            if (organization.hasAlias()) {
                terser.set("MSH-5-1", organization.getAlias().get(0).getValue());
            }
        }

        // Also populate EVN-7 (Event Facility) if present
        if (organization.hasAddress()) {
            Address addr = organization.getAddressFirstRep();
            try {
                if (addr.hasCity()) {
                    terser.set("EVN-7-3", addr.getCity());
                }
                if (addr.hasState()) {
                    terser.set("EVN-7-4", addr.getState());
                }
            } catch (Exception e) {
                // EVN segment may not exist in all message types
            }
        }

        // Populate PV1-3-4 (Facility) if organization is a facility
        if (organization.hasType()) {
            for (CodeableConcept type : organization.getType()) {
                if (type.hasCoding()) {
                    String code = type.getCodingFirstRep().getCode();
                    if ("prov".equalsIgnoreCase(code) || "hospital".equalsIgnoreCase(code)
                            || "facility".equalsIgnoreCase(code)) {
                        try {
                            terser.set("PV1-3-4-1", organization.getName());
                            if (organization.hasIdentifier()) {
                                terser.set("PV1-3-4-2", organization.getIdentifierFirstRep().getValue());
                            }
                        } catch (Exception e) {
                            // PV1 segment may not exist
                        }
                        break;
                    }
                }
            }
        }
    }
}
