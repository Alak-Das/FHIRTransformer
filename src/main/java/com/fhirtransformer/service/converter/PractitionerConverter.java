package com.fhirtransformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PractitionerConverter implements SegmentConverter<Practitioner> {

    @Override
    public List<Practitioner> convert(Terser terser, Bundle bundle, ConversionContext context) {
        Map<String, Practitioner> practitioners = new HashMap<>();

        // Helper to extractXCN
        extractFromXcn(terser, "/.PV1-7", practitioners);
        extractFromXcn(terser, "/.PV1-8", practitioners);
        extractFromXcn(terser, "/.PV1-9", practitioners);
        extractFromXcn(terser, "/.PV1-17", practitioners);
        extractFromXcn(terser, "/.PV1-52", practitioners);

        // Scan ORC segments
        int index = 0;
        while (true) {
            if (index > 50)
                break;
            String segmentPath = "/.ORC(" + index + ")";
            try {
                // Check if segment exists - check for presence of ORC-1
                String orcId = null;
                try {
                    orcId = terser.get(segmentPath + "-1");
                } catch (Exception e) {
                    break;
                }
                if (orcId == null || orcId.isEmpty()) {
                    break;
                }

                extractFromXcn(terser, segmentPath + "-12", practitioners);
                index++;
            } catch (Exception e) {
                break;
            }
        }

        return new ArrayList<>(practitioners.values());
    }

    private void extractFromXcn(Terser terser, String path, Map<String, Practitioner> practitioners) {
        try {
            String check = terser.get(path);
            if (check == null || check.isEmpty())
                return;

            String id = terser.get(path + "-1");
            if (id == null || id.isEmpty())
                return;

            if (practitioners.containsKey(id))
                return;

            Practitioner practitioner = new Practitioner();

            // Logic: ID = "practitioner-" + id to ensure it's a valid FHIR ID
            practitioner.setId("practitioner-" + id);

            Identifier identifier = new Identifier();
            identifier.setValue(id);
            practitioner.addIdentifier(identifier);

            String family = terser.get(path + "-2-1"); // FN -> Surname
            String given = terser.get(path + "-3");

            HumanName name = new HumanName();
            if (family != null)
                name.setFamily(family);
            if (given != null)
                name.addGiven(given);

            // Suffix/Prefix if available
            String prefix = terser.get(path + "-6");
            if (prefix != null)
                name.addPrefix(prefix);

            String suffix = terser.get(path + "-5");
            if (suffix != null)
                name.addSuffix(suffix);

            practitioner.addName(name);

            practitioners.put(id, practitioner);
            log.debug("Extracted Practitioner: {} {}", id, family);

        } catch (Exception e) {
            log.warn("Failed to extract practitioner from {}", path);
        }
    }
}
