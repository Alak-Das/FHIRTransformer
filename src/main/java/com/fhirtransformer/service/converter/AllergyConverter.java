package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
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
public class AllergyConverter implements SegmentConverter<AllergyIntolerance> {

    @Override
    public List<AllergyIntolerance> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<AllergyIntolerance> allergies = new ArrayList<>();
        int al1Index = 0;

        while (true) {
            if (al1Index > 50) {
                log.warn("Max AL1 segments reached for Allergy");
                break;
            }
            String al1Path = "/.AL1(" + al1Index + ")";
            String mainPathToUse = al1Path;
            boolean found = false;

            // Try generic/root path first
            try {
                if (terser.getSegment(al1Path) != null) {
                    found = true;
                }
            } catch (Exception e) {
                // Not found at root, try ADT structure
                String adtPath = "/.ALLERGY(" + al1Index + ")/AL1";
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
                String allergen = terser.get(mainPathToUse + "-3-1");

                if (allergen == null) {
                    log.warn("AL1 segment found at {} but missing code (3-1). Breaking.", mainPathToUse);
                    break;
                }

                AllergyIntolerance allergy = new AllergyIntolerance();
                allergy.setId(UUID.randomUUID().toString());
                if (context.getPatientId() != null) {
                    allergy.setPatient(new Reference("Patient/" + context.getPatientId()));
                }
                if (context.getEncounterId() != null) {
                    allergy.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/encounter-reference")
                            .setValue(new Reference("Encounter/" + context.getEncounterId()));
                }

                allergy.setVerificationStatus(new CodeableConcept().addCoding(
                        new Coding().setSystem(MappingConstants.SYSTEM_ALLERGY_VER_STATUS)
                                .setCode(MappingConstants.CODE_CONFIRMED)));
                allergy.setClinicalStatus(new CodeableConcept().addCoding(
                        new Coding().setSystem(MappingConstants.SYSTEM_ALLERGY_CLINICAL)
                                .setCode(MappingConstants.CODE_ACTIVE)));

                // AL1-2 Allergy Type
                String type = terser.get(mainPathToUse + "-2");
                if (type != null) {
                    if (MappingConstants.ALLERGY_TYPE_DRUG.equals(type)
                            || MappingConstants.ALLERGY_TYPE_MISC.equals(type)) {
                        allergy.addCategory(
                                AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
                    } else if (MappingConstants.ALLERGY_TYPE_FOOD.equals(type)) {
                        allergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
                    } else if (MappingConstants.ALLERGY_TYPE_ENV.equals(type) || "AA".equals(type)) { // AA = Animal
                                                                                                      // Allergy
                        allergy.addCategory(
                                AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT);
                    }
                }

                // AL1-3 Allergen Code/Text
                String allergenText = terser.get(mainPathToUse + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem(MappingConstants.SYSTEM_ICD10).setCode(allergen).setDisplay(allergenText);
                code.setText(allergenText);
                allergy.setCode(code);

                AllergyIntolerance.AllergyIntoleranceReactionComponent reactionComp = new AllergyIntolerance.AllergyIntoleranceReactionComponent();
                boolean hasReaction = false;

                // AL1-4 Severity -> Criticality and Reaction.severity
                String severity = terser.get(mainPathToUse + "-4-1"); // Get first component (SV, MO, MI)
                if (severity == null || severity.isEmpty()) {
                    severity = terser.get(mainPathToUse + "-4"); // Fallback
                }

                if (severity != null) {
                    // Map to Criticality
                    if (severity.startsWith("SV")) {
                        allergy.setCriticality(AllergyIntolerance.AllergyIntoleranceCriticality.HIGH);
                    } else {
                        allergy.setCriticality(AllergyIntolerance.AllergyIntoleranceCriticality.LOW);
                    }

                    // Map to Reaction Severity
                    if (severity.startsWith("MI"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MILD);
                    else if (severity.startsWith("MO"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MODERATE);
                    else if (severity.startsWith("SV"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.SEVERE);
                    hasReaction = true;
                }

                // AL1-5 Reaction (Repeating)
                int reactIndex = 0;
                while (true) {
                    String reactCode = terser.get(mainPathToUse + "-5(" + reactIndex + ")-1");
                    String reactText = terser.get(mainPathToUse + "-5(" + reactIndex + ")-2");
                    if (reactCode == null && reactText == null)
                        break;

                    CodeableConcept cc = new CodeableConcept();
                    if (reactCode != null)
                        cc.addCoding().setSystem(MappingConstants.SYSTEM_V2_0127).setCode(reactCode)
                                .setDisplay(reactText);
                    if (reactText != null)
                        cc.setText(reactText);

                    reactionComp.addManifestation(cc);
                    hasReaction = true;
                    reactIndex++;
                    if (reactIndex > 10)
                        break; // Safety
                }

                if (hasReaction) {
                    allergy.addReaction(reactionComp);
                }

                // AL1-6 Identification Date
                String onsetDate = terser.get(mainPathToUse + "-6");
                if (onsetDate != null && !onsetDate.isEmpty()) {
                    try {
                        Date date = Date.from(DateTimeUtil.parseHl7DateTime(onsetDate).toInstant());
                        if (date != null) {
                            allergy.setOnset(new DateTimeType(date));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse allergy onset date: {}", onsetDate);
                    }
                }

                allergies.add(allergy);
                al1Index++;
            } catch (Exception e) {
                log.warn("Error processing AL1 segment at index {}", al1Index, e);
                break;
            }
        }

        return allergies;
    }
}
