package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Converts FHIR Location to HL7 PV1 segment fields (PV1-3, PV1-6).
 * Location data is embedded in PV1 as part of patient visit information.
 */
@Component
public class LocationToPv1Converter implements FhirToHl7Converter<Location> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Location;
    }

    @Override
    public void convert(Location location, Message message, Terser terser) throws HL7Exception {
        // PV1-3 Assigned Patient Location (for current location)
        // PV1-6 Prior Patient Location
        // We'll populate PV1-3 for the primary location

        String pv1Path = "/.PV1";

        // Check if this is a prior location (via meta tag or extension)
        boolean isPrior = false;
        if (location.hasMeta() && location.getMeta().hasTag()) {
            for (Coding tag : location.getMeta().getTag()) {
                if ("prior".equalsIgnoreCase(tag.getCode())) {
                    isPrior = true;
                    break;
                }
            }
        }

        String fieldPath = isPrior ? pv1Path + "-6" : pv1Path + "-3";

        // PL.1 - Point of Care (Nursing Unit/Ward)
        if (location.hasName()) {
            terser.set(fieldPath + "-1", location.getName());
        }

        // PL.2 - Room
        // PL.3 - Bed
        // These would typically come from partOf hierarchy or physical type
        if (location.hasPhysicalType()) {
            CodeableConcept physType = location.getPhysicalType();
            if (physType.hasCoding()) {
                String typeCode = physType.getCodingFirstRep().getCode();
                // Map FHIR physical type to HL7 location components
                if ("ro".equals(typeCode) || "room".equalsIgnoreCase(typeCode)) {
                    terser.set(fieldPath + "-2", location.getName());
                } else if ("bd".equals(typeCode) || "bed".equalsIgnoreCase(typeCode)) {
                    terser.set(fieldPath + "-3", location.getName());
                } else if ("wa".equals(typeCode) || "ward".equalsIgnoreCase(typeCode)) {
                    terser.set(fieldPath + "-1", location.getName());
                } else if ("bu".equals(typeCode) || "building".equalsIgnoreCase(typeCode)) {
                    terser.set(fieldPath + "-7", location.getName()); // Building
                } else if ("wi".equals(typeCode) || "wing".equalsIgnoreCase(typeCode)) {
                    terser.set(fieldPath + "-8", location.getName()); // Floor
                }
            }
        }

        // PL.4 - Facility (HD)
        if (location.hasManagingOrganization()) {
            Reference org = location.getManagingOrganization();
            if (org.hasDisplay()) {
                terser.set(fieldPath + "-4-1", org.getDisplay());
            } else if (org.hasReference()) {
                String ref = org.getReference();
                if (ref.contains("/")) {
                    terser.set(fieldPath + "-4-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
        }

        // PL.5 - Location Status
        if (location.hasStatus()) {
            String status = location.getStatus().toCode();
            if ("active".equals(status)) {
                terser.set(fieldPath + "-5", "A"); // Active
            } else if ("inactive".equals(status)) {
                terser.set(fieldPath + "-5", "I"); // Inactive
            } else if ("suspended".equals(status)) {
                terser.set(fieldPath + "-5", "T"); // Temporary
            }
        }

        // PL.6 - Person Location Type
        if (location.hasMode()) {
            String mode = location.getMode().toCode();
            if ("instance".equals(mode)) {
                terser.set(fieldPath + "-6", "C"); // Clinic
            } else if ("kind".equals(mode)) {
                terser.set(fieldPath + "-6", "D"); // Department
            }
        }

        // PL.9 - Location Description
        if (location.hasDescription()) {
            terser.set(fieldPath + "-9", location.getDescription());
        }

        // Also map Location identifiers to PV1-19 (Visit Number) if present
        if (location.hasIdentifier()) {
            Identifier id = location.getIdentifierFirstRep();
            if (id.hasValue() && id.hasType()) {
                String typeCode = id.getType().getCodingFirstRep().getCode();
                if ("VN".equals(typeCode)) {
                    terser.set(pv1Path + "-19", id.getValue());
                }
            }
        }

        // Map address to PV1 location address fields if available
        if (location.hasAddress()) {
            Address addr = location.getAddress();
            // Location address typically goes to facility-level info
            if (addr.hasCity()) {
                terser.set(fieldPath + "-4-2", addr.getCity());
            }
            if (addr.hasState()) {
                terser.set(fieldPath + "-4-3", addr.getState());
            }
        }
    }
}
