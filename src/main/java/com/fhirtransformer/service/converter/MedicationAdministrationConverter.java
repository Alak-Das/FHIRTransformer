package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import com.fhirtransformer.util.DateTimeUtil;
import com.fhirtransformer.util.MappingConstants;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MedicationAdministrationConverter implements SegmentConverter<MedicationAdministration> {

    @Override
    public List<MedicationAdministration> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<MedicationAdministration> administrations = new ArrayList<>();
        int index = 0;

        while (true) {
            if (index > 50) {
                log.warn("Max RXA segments reached");
                break;
            }
            String segmentPath = "/.RXA(" + index + ")";
            String mainPathToUse = segmentPath;
            String mainOrcPath = "/.ORC(" + index + ")";
            boolean found = false;

            // Try generic/root path first
            try {
                if (terser.getSegment(segmentPath) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Not found at root, try ORDER group
                String adtPath = "/.ORDER(" + index + ")/RXA";
                try {
                    if (terser.getSegment(adtPath) != null) {
                        mainPathToUse = adtPath;
                        mainOrcPath = "/.ORDER(" + index + ")/ORC";
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
                // Check if segment exists
                String rxaId = null;
                try {
                    rxaId = terser.get(mainPathToUse + "-1");
                } catch (Exception e) {
                    break;
                }
                if (rxaId == null) {
                    break;
                }

                String code = terser.get(mainPathToUse + "-5-1");
                String system2 = terser.get(mainPathToUse + "-5-3");

                // If it is CVX, it is an Immunization, handled by ImmunizationConverter
                if ("CVX".equals(system2)) {
                    log.debug("Skipping RXA segment {} in MedicationAdministrationConverter (CVX found)",
                            mainPathToUse);
                    index++;
                    continue;
                }

                log.debug("Processing MedicationAdministration: {}", mainPathToUse);

                MedicationAdministration admin = new MedicationAdministration();
                admin.setId(UUID.randomUUID().toString());
                admin.setStatus(MedicationAdministration.MedicationAdministrationStatus.COMPLETED);

                if (context.getPatientId() != null) {
                    admin.setSubject(new Reference("Patient/" + context.getPatientId()));
                }
                if (context.getEncounterId() != null) {
                    admin.setContext(new Reference("Encounter/" + context.getEncounterId()));
                }

                // LINKING: Connect to MedicationRequest from Context
                try {
                    String appPlacerId = terser.get(mainOrcPath + "-2");
                    String appFillerId = terser.get(mainOrcPath + "-3");

                    MedicationRequest linkedRequest = null;
                    if (appPlacerId != null && !appPlacerId.isEmpty()) {
                        linkedRequest = context.getMedicationRequests().get("PLACER:" + appPlacerId);
                    }
                    if (linkedRequest == null && appFillerId != null && !appFillerId.isEmpty()) {
                        linkedRequest = context.getMedicationRequests().get("FILLER:" + appFillerId);
                    }

                    if (linkedRequest != null) {
                        admin.setRequest(new Reference("MedicationRequest/" + linkedRequest.getId()));
                        log.debug("Linked MedicationAdministration {} to MedicationRequest {}", admin.getId(),
                                linkedRequest.getId());
                    }
                } catch (Exception e) {
                    log.debug("Could not link MedicationAdministration to MedicationRequest at {}", mainOrcPath);
                }

                // Code
                String display = terser.get(mainPathToUse + "-5-2");
                if (code != null) {
                    CodeableConcept medication = new CodeableConcept();
                    Coding coding = new Coding();
                    coding.setSystem(MappingConstants.SYSTEM_RXNORM);
                    coding.setCode(code);
                    if (display != null)
                        coding.setDisplay(display);
                    medication.addCoding(coding);
                    admin.setMedication(medication);
                } else {
                    log.debug("No code found at {}", mainPathToUse + "-5-1");
                }

                // Effective Time (Start)
                String adminDate = terser.get(mainPathToUse + "-3");
                if (adminDate != null && !adminDate.isEmpty()) {
                    try {
                        admin.setEffective(DateTimeUtil.hl7DateTimeToFhir(adminDate));
                    } catch (Exception e) {
                        log.warn("Failed to parse administration date: {}", adminDate);
                    }
                }

                // Dosage (Quantity)
                String doseAmount = terser.get(mainPathToUse + "-6");
                String doseUnits = terser.get(mainPathToUse + "-7-1");

                if (doseAmount != null && doseUnits != null) {
                    try {
                        MedicationAdministration.MedicationAdministrationDosageComponent dosage = new MedicationAdministration.MedicationAdministrationDosageComponent();

                        Quantity doseQuantity = new Quantity();
                        doseQuantity.setValue(Double.parseDouble(doseAmount));
                        doseQuantity.setUnit(doseUnits);
                        doseQuantity.setSystem(MappingConstants.SYSTEM_UCUM);

                        dosage.setDose(doseQuantity);

                        String dosageForm = terser.get(mainPathToUse + "-8-2");
                        if (dosageForm != null) {
                            dosage.setText(dosageForm);
                        }

                        admin.setDosage(dosage);
                    } catch (Exception e) {
                        log.warn("Failed to parse dosage for RXA: {}", doseAmount);
                    }
                }

                administrations.add(admin);
                index++;

            } catch (Exception e) {
                log.error("Error processing RXA segment index {}", index, e);
                break;
            }
        }
        return administrations;
    }
}
