package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converter for creating FHIR Location resources from HL7 PV1-3 (Assigned
 * Patient Location).
 * 
 * PV1-3 Structure:
 * - PV1-3-1: Point of Care (Nursing Unit/Ward)
 * - PV1-3-2: Room
 * - PV1-3-3: Bed
 * - PV1-3-4: Facility (HD)
 * - PV1-3-5: Location Status
 * - PV1-3-6: Person Location Type
 * - PV1-3-7: Building
 * - PV1-3-8: Floor
 * - PV1-3-9: Location Description
 */
@Component
@Slf4j
public class LocationConverter implements SegmentConverter<Location> {

    private static final String LOCATION_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/location-physical-type";
    private static final String V2_LOCATION_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v2-0260";

    @Override
    public List<Location> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Location> locations = new ArrayList<>();

        try {
            // Try to find PV1 segment
            String mainPathToUse = "/.PV1";
            boolean found = false;

            try {
                if (terser.getSegment(mainPathToUse) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Try VISIT group path
                String visitPath = "/.VISIT/PV1";
                try {
                    if (terser.getSegment(visitPath) != null) {
                        mainPathToUse = visitPath;
                        found = true;
                    }
                } catch (Exception ex) {
                    // Not found
                }
            }

            if (!found) {
                return locations;
            }

            // Extract location components
            String pointOfCare = terser.get(mainPathToUse + "-3-1");
            String room = terser.get(mainPathToUse + "-3-2");
            String bed = terser.get(mainPathToUse + "-3-3");
            String facility = terser.get(mainPathToUse + "-3-4");
            String locationStatus = terser.get(mainPathToUse + "-3-5");
            String locationType = terser.get(mainPathToUse + "-3-6");
            String building = terser.get(mainPathToUse + "-3-7");
            String floor = terser.get(mainPathToUse + "-3-8");
            String locationDesc = terser.get(mainPathToUse + "-3-9");

            // Skip if no location data
            if (isEmpty(pointOfCare) && isEmpty(room) && isEmpty(bed) && isEmpty(building)) {
                return locations;
            }

            log.info("Processing Location from PV1-3: POC={}, Room={}, Bed={}, Facility={}",
                    pointOfCare, room, bed, facility);

            // Create hierarchical locations if detailed data is available
            Location primaryLocation = null;

            // Building location (if provided)
            Location buildingLocation = null;
            if (!isEmpty(building)) {
                buildingLocation = createLocation(building, null, "bu", "Building");
                buildingLocation.setDescription("Building: " + building);
                if (!isEmpty(facility)) {
                    buildingLocation.setManagingOrganization(
                            new Reference().setDisplay(facility));
                }
                locations.add(buildingLocation);
            }

            // Floor location (if provided)
            Location floorLocation = null;
            if (!isEmpty(floor)) {
                floorLocation = createLocation(floor, buildingLocation, "lvl", "Level");
                floorLocation.setDescription("Floor: " + floor);
                locations.add(floorLocation);
            }

            // Ward/Point of Care location
            Location wardLocation = null;
            if (!isEmpty(pointOfCare)) {
                Location parentLoc = floorLocation != null ? floorLocation : buildingLocation;
                wardLocation = createLocation(pointOfCare, parentLoc, "wa", "Ward");
                wardLocation.setDescription("Ward/Unit: " + pointOfCare);
                locations.add(wardLocation);
            }

            // Room location
            Location roomLocation = null;
            if (!isEmpty(room)) {
                Location parentLoc = wardLocation != null ? wardLocation
                        : (floorLocation != null ? floorLocation : buildingLocation);
                roomLocation = createLocation(room, parentLoc, "ro", "Room");
                roomLocation.setDescription("Room: " + room);
                locations.add(roomLocation);
            }

            // Bed location (most specific)
            if (!isEmpty(bed)) {
                Location parentLoc = roomLocation != null ? roomLocation
                        : (wardLocation != null ? wardLocation
                                : (floorLocation != null ? floorLocation : buildingLocation));
                Location bedLocation = createLocation(bed, parentLoc, "bd", "Bed");
                bedLocation.setDescription("Bed: " + bed);

                // Build full display name
                StringBuilder fullName = new StringBuilder();
                if (!isEmpty(pointOfCare))
                    fullName.append(pointOfCare);
                if (!isEmpty(room)) {
                    if (fullName.length() > 0)
                        fullName.append(" ");
                    fullName.append(room);
                }
                if (!isEmpty(bed)) {
                    if (fullName.length() > 0)
                        fullName.append("-");
                    fullName.append(bed);
                }
                bedLocation.setName(fullName.toString());

                locations.add(bedLocation);
                primaryLocation = bedLocation;
            } else if (roomLocation != null) {
                primaryLocation = roomLocation;
            } else if (wardLocation != null) {
                primaryLocation = wardLocation;
            } else if (!locations.isEmpty()) {
                primaryLocation = locations.get(locations.size() - 1);
            }

            // Store primary location ID in context
            if (primaryLocation != null && context != null) {
                context.setLocationId(primaryLocation.getId());
            }

            // Add location description if available
            if (primaryLocation != null && !isEmpty(locationDesc)) {
                String existingDesc = primaryLocation.getDescription();
                primaryLocation.setDescription(
                        (existingDesc != null ? existingDesc + " - " : "") + locationDesc);
            }

            // Set location status if provided
            if (primaryLocation != null && !isEmpty(locationStatus)) {
                try {
                    primaryLocation.setStatus(mapLocationStatus(locationStatus));
                } catch (Exception e) {
                    log.debug("Unknown location status: {}", locationStatus);
                }
            }

            log.info("Created {} Location resources from PV1-3", locations.size());

        } catch (Exception e) {
            log.error("Error converting Location from PV1-3", e);
        }

        return locations;
    }

    /**
     * Create a Location resource with standard structure.
     */
    private Location createLocation(String name, Location partOf, String physicalTypeCode, String physicalTypeDisplay) {
        Location location = new Location();
        location.setId(UUID.randomUUID().toString());
        location.setName(name);
        location.setStatus(Location.LocationStatus.ACTIVE);
        location.setMode(Location.LocationMode.INSTANCE);

        // Set physical type
        location.setPhysicalType(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem(LOCATION_TYPE_SYSTEM)
                        .setCode(physicalTypeCode)
                        .setDisplay(physicalTypeDisplay)));

        // Set part-of reference if parent exists
        if (partOf != null) {
            location.setPartOf(new Reference("Location/" + partOf.getId())
                    .setDisplay(partOf.getName()));
        }

        return location;
    }

    /**
     * Map HL7 location status to FHIR LocationStatus.
     */
    private Location.LocationStatus mapLocationStatus(String hl7Status) {
        if (hl7Status == null)
            return Location.LocationStatus.ACTIVE;

        switch (hl7Status.toUpperCase()) {
            case "A":
            case "ACTIVE":
                return Location.LocationStatus.ACTIVE;
            case "I":
            case "INACTIVE":
                return Location.LocationStatus.INACTIVE;
            case "S":
            case "SUSPENDED":
                return Location.LocationStatus.SUSPENDED;
            default:
                return Location.LocationStatus.ACTIVE;
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
