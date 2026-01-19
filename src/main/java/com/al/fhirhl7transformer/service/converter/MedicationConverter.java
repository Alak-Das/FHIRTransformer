package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MedicationConverter implements SegmentConverter<MedicationRequest> {

    public String getSegmentName() {
        return "RX_GROUP"; // Handles RXE, RXO, RXA
    }

    @Override
    public List<MedicationRequest> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<MedicationRequest> requests = new ArrayList<>();
        String[] medSegments = { "RXE", "RXO" };
        for (String segmentName : medSegments) {
            int segmentCount = 0;
            while (true) {
                if (segmentCount > 50) {
                    log.warn("Max medication segments reached for {}", segmentName);
                    break;
                }

                String segmentPath = "/." + segmentName + "(" + segmentCount + ")";
                String mainPathToUse = segmentPath;
                String mainOrcPath = "/.ORC(" + segmentCount + ")";
                boolean found = false;

                // Try generic/root path first
                try {
                    if (terser.getSegment(segmentPath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // Not found at root, try ORDER group (common in OML/OMP)
                    String adtPath = "/.ORDER(" + segmentCount + ")/" + segmentName;
                    try {
                        if (terser.getSegment(adtPath) != null) {
                            mainPathToUse = adtPath;
                            mainOrcPath = "/.ORDER(" + segmentCount + ")/ORC";
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
                    // Check if segment exists - check for presence of required field
                    String existenceCheck = null;
                    if ("RXE".equals(segmentName)) {
                        existenceCheck = terser.get(mainPathToUse + "-2"); // Give Code
                    } else if ("RXO".equals(segmentName)) {
                        existenceCheck = terser.get(mainPathToUse + "-1"); // Requested Give Code
                    }

                    if (existenceCheck == null || existenceCheck.isEmpty()) {
                        break;
                    }

                    log.debug("Processing medication group {}", mainPathToUse);

                    MedicationRequest medRequest = new MedicationRequest();
                    medRequest.setId(UUID.randomUUID().toString());
                    medRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
                    medRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
                    medRequest.setSubject(new Reference("Patient/" + context.getPatientId()));
                    if (context.getEncounterId() != null) {
                        medRequest.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                    }

                    // ORC Information (Placer/Filler IDs) for Linking
                    try {
                        String orcId = terser.get(mainOrcPath + "-1");
                        if (orcId != null && !orcId.isEmpty()) {
                            String placerId = terser.get(mainOrcPath + "-2");
                            String fillerId = terser.get(mainOrcPath + "-3");

                            if (placerId != null) {
                                medRequest.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                                        .setValue(placerId)
                                        .getType().addCoding().setCode("PLAC").setDisplay("Placer Identifier");
                                context.getMedicationRequests().put("PLACER:" + placerId, medRequest);
                            }
                            if (fillerId != null) {
                                medRequest.addIdentifier().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                                        .setValue(fillerId)
                                        .getType().addCoding().setCode("FILL").setDisplay("Filler Identifier");
                                context.getMedicationRequests().put("FILLER:" + fillerId, medRequest);
                            }

                            // ORC-12 Ordering Provider -> Requester
                            String providerId = terser.get(mainOrcPath + "-12-1");
                            String providerName = terser.get(mainOrcPath + "-12-2");
                            if (providerId != null || providerName != null) {
                                Reference requester = new Reference();
                                if (providerId != null)
                                    requester.setReference("Practitioner/" + providerId);
                                if (providerName != null) {
                                    String given = terser.get(mainOrcPath + "-12-3");
                                    StringBuilder name = new StringBuilder(providerName);
                                    if (given != null)
                                        name.append(", ").append(given);
                                    requester.setDisplay(name.toString());
                                }
                                medRequest.setRequester(requester);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Optional ORC for medication not found at {}", mainOrcPath);
                    }

                    // Map Medication Code
                    String code = null;
                    String display = null;
                    String system = MappingConstants.SYSTEM_RXNORM;

                    if ("RXE".equals(segmentName)) {
                        code = terser.get(mainPathToUse + "-2-1");
                        display = terser.get(mainPathToUse + "-2-2");

                        if (code == null) {
                            String raw = terser.get(mainPathToUse + "-2");
                            if (raw != null && raw.contains("^")) {
                                code = raw.split("\\^")[0];
                            } else {
                                code = raw;
                            }
                        }
                    } else if ("RXO".equals(segmentName)) {
                        code = terser.get(mainPathToUse + "-1-1");
                        display = terser.get(mainPathToUse + "-1-2");
                    }

                    if (code != null) {
                        CodeableConcept medication = new CodeableConcept();
                        Coding coding = new Coding();
                        coding.setSystem(system);
                        coding.setCode(code);
                        if (display != null)
                            coding.setDisplay(display);
                        medication.addCoding(coding);
                        medRequest.setMedication(medication);
                    } else {
                        log.warn("Skipping MedicationRequest for segment {} due to missing code", mainPathToUse);
                        segmentCount++;
                        continue;
                    }

                    // Dosage Instructions
                    Dosage dosage = new Dosage();
                    boolean hasDosageData = false;

                    if ("RXE".equals(segmentName)) {
                        String doseAmount = terser.get(mainPathToUse + "-3");
                        String doseUnits = terser.get(mainPathToUse + "-5-1");

                        if (doseAmount != null && doseUnits != null) {
                            try {
                                Quantity doseQuantity = new Quantity();
                                doseQuantity.setValue(Double.parseDouble(doseAmount));
                                doseQuantity.setUnit(doseUnits);
                                doseQuantity.setSystem(MappingConstants.SYSTEM_UCUM);
                                dosage.addDoseAndRate().setDose(doseQuantity);
                                hasDosageData = true;
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse dose amount: {}", doseAmount);
                            }
                        }

                        String instructions = terser.get(mainPathToUse + "-7-2");
                        if (instructions == null || instructions.isEmpty()) {
                            instructions = terser.get(mainPathToUse + "-7-1");
                        }
                        if (instructions == null || instructions.isEmpty()) {
                            instructions = terser.get(mainPathToUse + "-7");
                        }

                        if (instructions != null && !instructions.isEmpty()) {
                            dosage.setText(instructions);
                            hasDosageData = true;
                        }

                        String rateAmount = terser.get(mainPathToUse + "-21");
                        String rateUnits = terser.get(mainPathToUse + "-22-1");
                        if (rateAmount != null && rateUnits != null) {
                            try {
                                Quantity rateQuantity = new Quantity();
                                rateQuantity.setValue(Double.parseDouble(rateAmount));
                                rateQuantity.setUnit(rateUnits);
                                dosage.addDoseAndRate().setRate(rateQuantity);
                                hasDosageData = true;
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse rate amount: {}", rateAmount);
                            }
                        }

                        String dispenseAmount = terser.get(mainPathToUse + "-10");
                        String dispenseUnits = terser.get(mainPathToUse + "-11-1");
                        String refills = terser.get(mainPathToUse + "-12");

                        if ((dispenseAmount != null && !dispenseAmount.isEmpty())
                                || (refills != null && !refills.isEmpty())) {
                            MedicationRequest.MedicationRequestDispenseRequestComponent dispenseRequest = new MedicationRequest.MedicationRequestDispenseRequestComponent();

                            if (dispenseAmount != null) {
                                try {
                                    Quantity quantity = new Quantity();
                                    quantity.setValue(Double.parseDouble(dispenseAmount));
                                    if (dispenseUnits != null)
                                        quantity.setUnit(dispenseUnits);
                                    dispenseRequest.setQuantity(quantity);
                                } catch (NumberFormatException e) {
                                    log.warn("Could not parse dispense amount: {}", dispenseAmount);
                                }
                            }

                            if (refills != null) {
                                try {
                                    dispenseRequest.setNumberOfRepeatsAllowed(Integer.parseInt(refills));
                                } catch (NumberFormatException e) {
                                    log.warn("Could not parse refills: {}", refills);
                                }
                            }
                            medRequest.setDispenseRequest(dispenseRequest);
                        }

                    } else if ("RXO".equals(segmentName)) {
                        String doseAmount = terser.get(mainPathToUse + "-2");
                        String doseUnits = terser.get(mainPathToUse + "-4-1");

                        if (doseAmount != null && doseUnits != null) {
                            try {
                                Quantity doseQuantity = new Quantity();
                                doseQuantity.setValue(Double.parseDouble(doseAmount));
                                doseQuantity.setUnit(doseUnits);
                                dosage.addDoseAndRate().setDose(doseQuantity);
                                hasDosageData = true;
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse RXO dose amount: {}", doseAmount);
                            }
                        }
                    }

                    if (hasDosageData) {
                        medRequest.addDosageInstruction(dosage);
                    }

                    requests.add(medRequest);
                    log.debug("Mapped MedicationRequest: {}", medRequest.getId());
                    segmentCount++;
                } catch (Exception e) {
                    log.error("Error converting {} at {}", segmentName, mainPathToUse, e);
                    segmentCount++;
                }
            }
        }
        return requests;
    }
}
