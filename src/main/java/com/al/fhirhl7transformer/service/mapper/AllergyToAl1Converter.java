package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.util.MappingConstants;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

@Component
public class AllergyToAl1Converter implements FhirToHl7Converter<AllergyIntolerance> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof AllergyIntolerance;
    }

    @Override
    public void convert(AllergyIntolerance allergy, Message message, Terser terser) throws HL7Exception {
        if (!(message instanceof ADT_A01)) {
            return;
        }

        ADT_A01 adt = (ADT_A01) message;
        int al1Index = adt.getAL1Reps();
        // Force creation of segment by accessing it
        adt.getAL1(al1Index);

        String al1Path = "/.AL1(" + al1Index + ")";

        // AL1-1 Set ID
        terser.set(al1Path + "-1", String.valueOf(al1Index + 1));

        // AL1-2 Allergen Type Code
        if (allergy.hasCategory() && !allergy.getCategory().isEmpty()) {
            String cat = allergy.getCategory().get(0).getValue().toCode();
            if ("medication".equalsIgnoreCase(cat))
                terser.set(al1Path + "-2", MappingConstants.ALLERGY_TYPE_DRUG); // Drug
            else if ("food".equalsIgnoreCase(cat))
                terser.set(al1Path + "-2", MappingConstants.ALLERGY_TYPE_FOOD); // Food
            else
                terser.set(al1Path + "-2", MappingConstants.ALLERGY_TYPE_ENV); // Environmental/Other
        }

        // AL1-3 Allergen Code/Mnemonic/Description
        if (allergy.hasCode() && allergy.getCode().hasCoding()) {
            terser.set(al1Path + "-3-1", allergy.getCode().getCodingFirstRep().getCode());
            terser.set(al1Path + "-3-2", allergy.getCode().getCodingFirstRep().getDisplay());
        }

        // AL1-5 Allergy Reaction
        if (allergy.hasReaction()) {
            // Take first reaction manifestation
            if (!allergy.getReactionFirstRep().getManifestation().isEmpty()) {
                terser.set(al1Path + "-5",
                        allergy.getReactionFirstRep().getManifestationFirstRep().getText());
            }
        }
    }
}
