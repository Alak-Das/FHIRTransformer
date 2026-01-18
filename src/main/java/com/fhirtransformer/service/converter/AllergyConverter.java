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
            try {
                String al1Path = "/.AL1(" + al1Index + ")";
                String allergen = terser.get(al1Path + "-3-1");

                if (allergen == null)
                    break;

                AllergyIntolerance allergy = new AllergyIntolerance();
                allergy.setId(UUID.randomUUID().toString());
                if (context.getPatientId() != null) {
                    allergy.setPatient(new Reference("Patient/" + context.getPatientId()));
                }

                allergy.setVerificationStatus(new CodeableConcept().addCoding(
                        new Coding().setSystem(MappingConstants.SYSTEM_ALLERGY_VER_STATUS)
                                .setCode(MappingConstants.CODE_CONFIRMED)));
                allergy.setClinicalStatus(new CodeableConcept().addCoding(
                        new Coding().setSystem(MappingConstants.SYSTEM_ALLERGY_CLINICAL)
                                .setCode(MappingConstants.CODE_ACTIVE)));

                // AL1-2 Allergy Type
                String type = terser.get(al1Path + "-2");
                if (type != null) {
                    if (MappingConstants.ALLERGY_TYPE_DRUG.equals(type)
                            || MappingConstants.ALLERGY_TYPE_MISC.equals(type)) {
                        allergy.addCategory(
                                AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
                    } else if (MappingConstants.ALLERGY_TYPE_FOOD.equals(type)) {
                        allergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
                    } else if (MappingConstants.ALLERGY_TYPE_ENV.equals(type)) {
                        allergy.addCategory(
                                AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT);
                    }
                }

                // AL1-3 Allergen Code/Text
                String allergenText = terser.get(al1Path + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem("http://hl7.org/fhir/sid/icd-10").setCode(allergen).setDisplay(allergenText);
                code.setText(allergenText);
                allergy.setCode(code);

                AllergyIntolerance.AllergyIntoleranceReactionComponent reactionComp = new AllergyIntolerance.AllergyIntoleranceReactionComponent();
                boolean hasReaction = false;

                // AL1-4 Severity
                String severity = terser.get(al1Path + "-4-1"); // Get first component (SV, MO, MI)
                if (severity == null || severity.isEmpty()) {
                    severity = terser.get(al1Path + "-4"); // Fallback
                }

                if (severity != null) {
                    if (severity.startsWith("MI"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MILD);
                    else if (severity.startsWith("MO"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MODERATE);
                    else if (severity.startsWith("SV"))
                        reactionComp.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.SEVERE);
                    hasReaction = true;
                }

                // AL1-5 Reaction
                String reactionText = terser.get(al1Path + "-5");
                if (reactionText != null && !reactionText.isEmpty()) {
                    reactionComp.addManifestation(new CodeableConcept().setText(reactionText));
                    hasReaction = true;
                }

                if (hasReaction) {
                    allergy.addReaction(reactionComp);
                }

                // AL1-6 Identification Date
                String onsetDate = terser.get(al1Path + "-6");
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
