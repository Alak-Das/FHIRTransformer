package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;
import ca.uhn.hl7v2.model.Group;
import com.fhirtransformer.util.DateTimeUtil;
import com.fhirtransformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ObservationConverter implements SegmentConverter<Observation> {

    @Override
    public List<Observation> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Observation> observations = new ArrayList<>();
        int obxIndex = 0;

        while (true) {
            if (obxIndex > 50) {
                log.warn("Max OBX segments reached for Observation");
                break;
            }
            String obxPath = "/.OBX(" + obxIndex + ")";
            String mainPathToUse = obxPath;
            boolean found = false;

            // Try generic/root path first
            try {
                if (terser.getSegment(obxPath) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Not found at root, try ADT structure
                String adtPath = "/.OBSERVATION(" + obxIndex + ")/OBX";
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
                terser.get(mainPathToUse + "-1");
                String obx3 = terser.get(mainPathToUse + "-3-1");

                if (obx3 == null) {
                    log.warn("OBX segment found at {} but missing code (3-1). Breaking.", mainPathToUse);
                    break;
                }

                Observation observation = new Observation();
                observation.setId(UUID.randomUUID().toString());
                if (context.getPatientId() != null) {
                    observation.setSubject(new Reference("Patient/" + context.getPatientId()));
                }
                if (context.getEncounterId() != null) {
                    observation.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }
                observation.setStatus(Observation.ObservationStatus.FINAL);

                String obx3Text = terser.get(mainPathToUse + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem(MappingConstants.SYSTEM_LOINC).setCode(obx3).setDisplay(obx3Text);
                observation.setCode(code);

                String value = terser.get(mainPathToUse + "-5-1");
                String units = terser.get(mainPathToUse + "-6-1");

                if (value != null && !value.isEmpty()) {
                    try {
                        double val = Double.parseDouble(value);
                        Quantity quantity = new Quantity();
                        quantity.setValue(val);
                        if (units != null)
                            quantity.setUnit(units);
                        observation.setValue(quantity);
                    } catch (NumberFormatException e) {
                        observation.setValue(new StringType(value));
                    }
                }

                // OBX-7 Reference Range
                String refRange = terser.get(mainPathToUse + "-7");
                if (refRange != null && !refRange.isEmpty()) {
                    observation.addReferenceRange().setText(refRange);
                }

                String status = terser.get(mainPathToUse + "-11");
                if (status != null) {
                    switch (status) {
                        case "F":
                            observation.setStatus(Observation.ObservationStatus.FINAL);
                            break;
                        case "P":
                            observation.setStatus(Observation.ObservationStatus.PRELIMINARY);
                            break;
                        case "C":
                            observation.setStatus(Observation.ObservationStatus.AMENDED);
                            break;
                        case "X":
                            observation.setStatus(Observation.ObservationStatus.CANCELLED);
                            break;
                        case "W":
                            observation.setStatus(Observation.ObservationStatus.ENTEREDINERROR);
                            break;
                        default:
                            observation.setStatus(Observation.ObservationStatus.FINAL);
                            break;
                    }
                }

                // OBX-8 Interpretation (Abnormal Flags)
                String interpretation = terser.get(mainPathToUse + "-8");
                if (interpretation != null) {
                    observation.addInterpretation().addCoding()
                            .setSystem(MappingConstants.SYSTEM_OBSERVATION_INTERPRETATION)
                            .setCode(interpretation);
                }

                // OBX-14 Date/Time of the Observation
                String effectiveDateStr = terser.get(mainPathToUse + "-14");
                if (effectiveDateStr != null && !effectiveDateStr.isEmpty()) {
                    try {
                        Date date = Date.from(DateTimeUtil.parseHl7DateTime(effectiveDateStr).toInstant());
                        if (date != null) {
                            observation.setEffective(new DateTimeType(date));
                        }
                    } catch (Exception e) {
                    } // Close catch
                } // Close if (effectiveDateStr != null)

                // OBX-16 Responsible Observer -> Performer
                String observerId = terser.get(mainPathToUse + "-16-1");
                String observerName = terser.get(mainPathToUse + "-16-2");
                if (observerId != null || observerName != null) {
                    Reference performer = new Reference();
                    if (observerId != null)
                        performer.setReference("Practitioner/" + observerId);
                    if (observerName != null)
                        performer.setDisplay(observerName);
                    observation.addPerformer(performer);
                }

                // Check for NTE segments (Notes) associated with this OBX

                // ...

                try {
                    Segment obxSegment = terser.getSegment(mainPathToUse);
                    if (obxSegment != null) {
                        Structure parent = obxSegment.getParent();
                        if (parent instanceof Group) {
                            Group group = (Group) parent;

                            // 1. Group by Order ID for DiagnosticReport linking
                            try {
                                // Robustly determine OBR path: strip "OBX" and any trailing chars
                                int obxIdx = mainPathToUse.lastIndexOf("OBX");
                                String obrPath = "";
                                if (obxIdx > 0) {
                                    obrPath = mainPathToUse.substring(0, obxIdx);
                                    // If what remains ends in /, append OBR. If ends in /., append OBR.
                                    // If result is empty or just /, append OBR.
                                    if (obrPath.endsWith("/") || obrPath.endsWith(".")) {
                                        obrPath += "OBR";
                                    } else {
                                        // Fallback? usually shouldn't happen if we strip OBX
                                        obrPath += "/OBR";
                                    }
                                } else {
                                    // Fallback
                                    obrPath = "/.OBR";
                                }
                                if (!obrPath.contains("OBR")) {
                                    // Try to find OBR in parent group
                                    obrPath = mainPathToUse.substring(0, mainPathToUse.lastIndexOf("/")) + "/OBR";
                                }
                                String placerId = terser.get(obrPath + "-2-1");
                                String fillerId = terser.get(obrPath + "-3-1");

                                String part = null; // Initialize part to null or default value
                                if (placerId != null) {
                                    context.getObservationsByObr()
                                            .computeIfAbsent(placerId.hashCode(), k -> new ArrayList<>())
                                            .add(observation);
                                    log.info("Linked Observation {} to OBR Placer={}", observation.getId(), placerId);
                                } else if (fillerId != null) {
                                    context.getObservationsByObr()
                                            .computeIfAbsent(fillerId.hashCode(), k -> new ArrayList<>())
                                            .add(observation);
                                    log.info("Linked Observation {} to OBR Filler={}", observation.getId(), fillerId);
                                } else {
                                    // Fallback to index if no IDs
                                    part = mainPathToUse.contains("(")
                                            ? mainPathToUse.substring(mainPathToUse.indexOf("(") + 1,
                                                    mainPathToUse.indexOf(")"))
                                            : "0";
                                    context.getObservationsByObr()
                                            .computeIfAbsent(Integer.parseInt(part), k -> new ArrayList<>())
                                            .add(observation);
                                    log.info("Linked Observation {} to OBR Index={}", observation.getId(), part);
                                }
                            } catch (Exception e) {
                                log.debug("Could not determine OBR link for OBX: {}", e.getMessage());
                            }

                            // 2. Safe check if NTE exists in this group's definition
                            boolean hasNte = false;
                            try {
                                String[] names = group.getNames();
                                for (String name : names) {
                                    if ("NTE".equals(name)) {
                                        hasNte = true;
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore
                            }

                            if (hasNte) {
                                Structure[] ntes = group.getAll("NTE");
                                for (Structure nteStruct : ntes) {
                                    if (nteStruct instanceof Segment) {
                                        Segment nte = (Segment) nteStruct;
                                        // NTE-3: Comment
                                        ca.uhn.hl7v2.model.Type[] comments = nte.getField(3);
                                        for (ca.uhn.hl7v2.model.Type c : comments) {
                                            String commentText = c.toString();
                                            if (!commentText.isEmpty()) {
                                                Annotation annotation = new Annotation();
                                                annotation.setText(commentText);
                                                observation.addNote(annotation);
                                                log.debug("Mapped NTE-3 to Observation.note: {}", commentText);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing OBX NTEs: {}", e.getMessage());
                }

                observations.add(observation);
                obxIndex++;
            } catch (Exception e) {
                log.warn("Error processing OBX segment at index {}", obxIndex, e);
                break;
            }
        }

        return observations;
    }
}
