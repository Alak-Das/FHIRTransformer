package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.util.DateTimeUtil;
import com.al.fhirhl7transformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class ImmunizationConverter implements SegmentConverter<Immunization> {

    @Override
    public List<Immunization> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Immunization> immunizations = new ArrayList<>();
        log.debug("Processing Immunization segments...");
        int index = 0;
        while (true) {
            if (index > 50) {
                break; // Safety break to prevent infinite loops
            }
            String rxaPath = "/.RXA(" + index + ")";
            String mainPathToUse = rxaPath;
            boolean found = false;

            // Try generic/root path first
            try {
                if (terser.getSegment(rxaPath) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Not found at root, try ORDER group (common in VXU)
                String adtPath = "/.ORDER(" + index + ")/RXA";
                try {
                    if (terser.getSegment(adtPath) != null) {
                        mainPathToUse = adtPath;
                        found = true;
                    }
                } catch (Exception ex) {
                    // Not found
                }
            }

            if (!found) {
                break;
            }

            try {
                // Check if segment exists - check for presence of RXA-1
                String rxaId = terser.get(mainPathToUse + "-1");
                if (rxaId == null || rxaId.isEmpty()) {
                    break;
                }

                String vaccineCode = terser.get(mainPathToUse + "-5-1");
                if (vaccineCode == null)
                    break;

                // Vaccine Code Check - Must be CVX
                String system = terser.get(mainPathToUse + "-5-3");
                if (system == null || !"CVX".equals(system)) {
                    log.debug("Skipping RXA segment {} in Immunization processing (Not CVX)", mainPathToUse);
                    index++;
                    continue;
                }

                Immunization immunization = new Immunization();
                immunization.setId(UUID.randomUUID().toString());

                // Status
                String status = terser.get(mainPathToUse + "-20");
                if ("CP".equals(status)) {
                    immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED);
                } else if ("NA".equals(status)) {
                    immunization.setStatus(Immunization.ImmunizationStatus.NOTDONE);
                } else {
                    immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED); // Default
                }

                // Vaccine Code
                immunization.getVaccineCode().addCoding()
                        .setSystem(MappingConstants.SYSTEM_CVX)
                        .setCode(vaccineCode)
                        .setDisplay(terser.get(mainPathToUse + "-5-2"));

                // Patient
                if (context.getPatientId() != null) {
                    immunization.setPatient(new Reference("Patient/" + context.getPatientId()));
                }
                if (context.getEncounterId() != null) {
                    immunization.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }

                // Date/Time
                String adminDate = terser.get(mainPathToUse + "-3");
                if (adminDate != null && !adminDate.isEmpty()) {
                    DateTimeType dateType = DateTimeUtil.hl7DateTimeToFhir(adminDate);
                    if (dateType != null) {
                        immunization.setOccurrence(dateType);
                    }
                }

                // Lot Number
                String lot = terser.get(mainPathToUse + "-15");
                if (lot != null)
                    immunization.setLotNumber(lot);

                // Manufacturer
                String manufacturerName = terser.get(mainPathToUse + "-17-2");
                if (manufacturerName != null) {
                    Reference manufacturerRef = processOrganization(terser, mainPathToUse + "-17", bundle);
                    immunization.setManufacturer(manufacturerRef);
                }

                // Performer
                String performerId = terser.get(mainPathToUse + "-10-1");
                if (performerId != null) {
                    Reference performerRef = processPractitioner(terser, mainPathToUse + "-10", bundle);
                    immunization.addPerformer().setActor(performerRef);
                }

                // RXA-9 Administration Notes -> Note
                ca.uhn.hl7v2.model.Type[] noteTypes = terser.getSegment(mainPathToUse).getField(9);
                for (ca.uhn.hl7v2.model.Type t : noteTypes) {
                    if (t != null && !t.isEmpty()) {
                        immunization.addNote(new Annotation().setText(t.toString()));
                    }
                }

                // RXA-16 Expiration Date
                String expirationDate = terser.get(mainPathToUse + "-16");
                if (expirationDate != null && !expirationDate.isEmpty()) {
                    try {
                        Date exp = Date.from(DateTimeUtil.parseHl7DateTime(expirationDate).toInstant());
                        immunization.setExpirationDate(exp);
                    } catch (Exception e) {
                        log.warn("Failed to parse immunization expiration date: {}", expirationDate);
                    }
                }

                // RXA-6 Administered Amount & RXA-7 Administered Units
                // HL7 v2.5 standard: RXA-6 is Amount, RXA-7 is Units
                String doseAmount = terser.get(mainPathToUse + "-6");
                if (doseAmount != null && !doseAmount.isEmpty()) {
                    try {
                        SimpleQuantity dose = new SimpleQuantity();
                        dose.setValue(new java.math.BigDecimal(doseAmount));

                        String unitId = terser.get(mainPathToUse + "-7-1");
                        String unitText = terser.get(mainPathToUse + "-7-2");
                        String unitSystem = terser.get(mainPathToUse + "-7-3");

                        if (unitId != null)
                            dose.setCode(unitId);
                        if (unitText != null)
                            dose.setUnit(unitText);
                        if (unitSystem != null)
                            dose.setSystem(unitSystem); // e.g. UCUM

                        immunization.setDoseQuantity(dose);
                    } catch (Exception e) {
                        log.warn("Failed to parse dose quantity: {}", doseAmount);
                    }
                }

                // Check for sibling RXR segment (Route/Site)
                try {
                    ca.uhn.hl7v2.model.Segment rxaSegment = terser.getSegment(mainPathToUse);
                    if (rxaSegment != null && rxaSegment.getParent() instanceof ca.uhn.hl7v2.model.Group) {
                        ca.uhn.hl7v2.model.Group group = (ca.uhn.hl7v2.model.Group) rxaSegment.getParent();
                        // Look for RXR in the same group
                        String[] childNames = group.getNames();
                        boolean hasRXR = false;
                        for (String name : childNames) {
                            if ("RXR".equals(name)) {
                                hasRXR = true;
                                break;
                            }
                        }

                        if (hasRXR) {
                            ca.uhn.hl7v2.model.Structure[] rxrs = group.getAll("RXR");
                            for (ca.uhn.hl7v2.model.Structure s : rxrs) {
                                if (s instanceof ca.uhn.hl7v2.model.Segment) {
                                    ca.uhn.hl7v2.model.Segment rxr = (ca.uhn.hl7v2.model.Segment) s;

                                    // RXR-1 Route
                                    ca.uhn.hl7v2.model.Type routeType = rxr.getField(1, 0);
                                    if (routeType instanceof ca.uhn.hl7v2.model.Composite) {
                                        ca.uhn.hl7v2.model.Composite comp = (ca.uhn.hl7v2.model.Composite) routeType;
                                        String routeId = safeGetValue(
                                                (ca.uhn.hl7v2.model.Primitive) comp.getComponent(0));
                                        String routeText = safeGetValue(
                                                (ca.uhn.hl7v2.model.Primitive) comp.getComponent(1));
                                        String routeSys = safeGetValue(
                                                (ca.uhn.hl7v2.model.Primitive) comp.getComponent(2));

                                        if (routeId != null) {
                                            CodeableConcept route = new CodeableConcept();
                                            route.addCoding().setCode(routeId).setDisplay(routeText)
                                                    .setSystem(routeSys);
                                            immunization.setRoute(route);
                                        }
                                    }

                                    // RXR-2 Site
                                    ca.uhn.hl7v2.model.Type siteType = rxr.getField(2, 0);
                                    if (siteType instanceof ca.uhn.hl7v2.model.Composite) {
                                        ca.uhn.hl7v2.model.Composite comp = (ca.uhn.hl7v2.model.Composite) siteType;
                                        String siteId = safeGetValue(
                                                (ca.uhn.hl7v2.model.Primitive) comp.getComponent(0));
                                        String siteText = safeGetValue(
                                                (ca.uhn.hl7v2.model.Primitive) comp.getComponent(1));
                                        String siteSys = safeGetValue(
                                                (ca.uhn.hl7v2.model.Primitive) comp.getComponent(2));

                                        if (siteId != null) {
                                            CodeableConcept site = new CodeableConcept();
                                            site.addCoding().setCode(siteId).setDisplay(siteText).setSystem(siteSys);
                                            immunization.setSite(site);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing RXR for RXA index {}", index, e);
                }

                immunizations.add(immunization);
                index++;
            } catch (Exception e) {
                log.error("Error converting Immunization at {}: {}", mainPathToUse, e.getMessage());
                index++;
            }
        }
        return immunizations;
    }

    private String safeGetValue(ca.uhn.hl7v2.model.Primitive p) {
        return p != null ? p.getValue() : null;
    }

    private Reference processPractitioner(Terser terser, String path, Bundle bundle) throws Exception {
        String id = terser.get(path + "-1");
        if (id == null)
            return null;

        // Check if already in bundle to avoid duplicates
        Optional<Bundle.BundleEntryComponent> existing = bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof Practitioner && e.getResource().getId().contains(id))
                .findFirst();

        if (existing.isPresent()) {
            return new Reference("Practitioner/" + existing.get().getResource().getId());
        }

        Practitioner practitioner = new Practitioner();
        practitioner.setId(UUID.randomUUID().toString());
        practitioner.addIdentifier()
                .setSystem(MappingConstants.SYSTEM_PRACTITIONER_ID)
                .setValue(id);

        String family = terser.get(path + "-2");
        String given = terser.get(path + "-3");
        if (family != null || given != null) {
            HumanName name = practitioner.addName().setFamily(family);
            if (given != null)
                name.addGiven(given);
        }

        bundle.addEntry().setResource(practitioner).getRequest()
                .setMethod(Bundle.HTTPVerb.POST)
                .setUrl("Practitioner");

        return new Reference("Practitioner/" + practitioner.getId());
    }

    private Reference processOrganization(Terser terser, String path, Bundle bundle) throws Exception {
        String id = terser.get(path + "-1");
        String name = terser.get(path + "-2");
        if (name == null)
            name = id;
        if (name == null)
            return null;

        // Simple deduplication by name
        final String finalName = name;
        Optional<Bundle.BundleEntryComponent> existing = bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof Organization
                        && ((Organization) e.getResource()).getName().equals(finalName))
                .findFirst();

        if (existing.isPresent()) {
            return new Reference("Organization/" + existing.get().getResource().getId());
        }

        Organization org = new Organization();
        org.setId(UUID.randomUUID().toString());
        org.setName(name);

        bundle.addEntry().setResource(org).getRequest()
                .setMethod(Bundle.HTTPVerb.POST)
                .setUrl("Organization");

        return new Reference("Organization/" + org.getId());
    }
}
