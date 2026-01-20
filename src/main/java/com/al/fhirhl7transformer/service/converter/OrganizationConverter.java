package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Converter for creating FHIR Organization resources from HL7 MSH segment.
 * 
 * Sources:
 * - MSH-3: Sending Application
 * - MSH-4: Sending Facility (primary source)
 * - MSH-5: Receiving Application
 * - MSH-6: Receiving Facility
 * - PV1-3-4: Assigned Patient Location - Facility
 */
@Component
@Slf4j
public class OrganizationConverter implements SegmentConverter<Organization> {

    private static final String ORG_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/organization-type";
    private static final String HL7_FACILITY_SYSTEM = "http://terminology.hl7.org/2.16.840.1.113883.18.23";

    @Override
    public List<Organization> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Organization> organizations = new ArrayList<>();
        Set<String> addedFacilities = new HashSet<>();

        try {
            // Extract MSH facility information
            String sendingApp = terser.get("/.MSH-3");
            String sendingFacility = terser.get("/.MSH-4");
            String receivingApp = terser.get("/.MSH-5");
            String receivingFacility = terser.get("/.MSH-6");

            log.info("Processing Organizations from MSH: SendFacility={}, RecvFacility={}",
                    sendingFacility, receivingFacility);

            // Create Sending Facility Organization
            if (!isEmpty(sendingFacility)) {
                Organization sendingOrg = createFacilityOrganization(
                        sendingFacility, sendingApp, "prov", "Healthcare Provider", true);
                organizations.add(sendingOrg);
                addedFacilities.add(sendingFacility.toUpperCase());

                if (context != null) {
                    context.setSendingOrganizationId(sendingOrg.getId());
                }
            }

            // Create Receiving Facility Organization (if different from sending)
            if (!isEmpty(receivingFacility) &&
                    !addedFacilities.contains(receivingFacility.toUpperCase())) {
                Organization receivingOrg = createFacilityOrganization(
                        receivingFacility, receivingApp, "prov", "Healthcare Provider", false);
                organizations.add(receivingOrg);
                addedFacilities.add(receivingFacility.toUpperCase());

                if (context != null) {
                    context.setReceivingOrganizationId(receivingOrg.getId());
                }
            }

            // Extract PV1-3-4 Facility (if present and different)
            try {
                String pv1Facility = terser.get("/.PV1-3-4");
                if (!isEmpty(pv1Facility) && !addedFacilities.contains(pv1Facility.toUpperCase())) {
                    Organization pv1Org = createFacilityOrganization(
                            pv1Facility, null, "dept", "Hospital Department", false);
                    organizations.add(pv1Org);
                    addedFacilities.add(pv1Facility.toUpperCase());
                }
            } catch (Exception e) {
                // PV1 may not exist
            }

            // Try VISIT group path for PV1
            try {
                String pv1Facility = terser.get("/.VISIT/PV1-3-4");
                if (!isEmpty(pv1Facility) && !addedFacilities.contains(pv1Facility.toUpperCase())) {
                    Organization pv1Org = createFacilityOrganization(
                            pv1Facility, null, "dept", "Hospital Department", false);
                    organizations.add(pv1Org);
                    addedFacilities.add(pv1Facility.toUpperCase());
                }
            } catch (Exception e) {
                // VISIT group may not exist
            }

            log.info("Created {} Organization resources", organizations.size());

        } catch (Exception e) {
            log.error("Error converting Organizations from MSH", e);
        }

        return organizations;
    }

    /**
     * Create an Organization resource for a healthcare facility.
     *
     * @param facilityName    The name of the facility
     * @param applicationName The associated application name (optional)
     * @param typeCode        Organization type code
     * @param typeDisplay     Organization type display
     * @param isPrimary       Whether this is the primary/sending facility
     * @return The created Organization resource
     */
    private Organization createFacilityOrganization(
            String facilityName,
            String applicationName,
            String typeCode,
            String typeDisplay,
            boolean isPrimary) {

        Organization org = new Organization();
        org.setId(UUID.randomUUID().toString());
        org.setActive(true);
        org.setName(facilityName);

        // Add identifier
        Identifier identifier = org.addIdentifier();
        identifier.setSystem(HL7_FACILITY_SYSTEM);
        identifier.setValue(facilityName);
        if (isPrimary) {
            identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
        }

        // Add application as secondary identifier if available
        if (!isEmpty(applicationName)) {
            Identifier appIdentifier = org.addIdentifier();
            appIdentifier.setSystem("http://terminology.hl7.org/CodeSystem/v2-0361");
            appIdentifier.setValue(applicationName);
            appIdentifier.setUse(Identifier.IdentifierUse.SECONDARY);
            appIdentifier.getType().setText("Sending Application");
        }

        // Set organization type
        CodeableConcept type = new CodeableConcept();
        type.addCoding(new Coding()
                .setSystem(ORG_TYPE_SYSTEM)
                .setCode(typeCode)
                .setDisplay(typeDisplay));
        org.addType(type);

        // Add tag to identify sending vs receiving
        if (isPrimary) {
            org.getMeta().addTag()
                    .setSystem("http://example.org/fhir/organization-role")
                    .setCode("sender")
                    .setDisplay("Message Sender");
        }

        return org;
    }

    /**
     * Get reference to the sending organization from context.
     */
    public Reference getSendingOrganizationReference(ConversionContext context) {
        if (context != null && context.getSendingOrganizationId() != null) {
            return new Reference("Organization/" + context.getSendingOrganizationId());
        }
        return null;
    }

    /**
     * Get reference to the receiving organization from context.
     */
    public Reference getReceivingOrganizationReference(ConversionContext context) {
        if (context != null && context.getReceivingOrganizationId() != null) {
            return new Reference("Organization/" + context.getReceivingOrganizationId());
        }
        return null;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
