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
public class ObservationConverter implements SegmentConverter<Observation> {

    @Override
    public List<Observation> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Observation> observations = new ArrayList<>();
        int obxIndex = 0;

        while (true) {
            try {
                String obxPath = "/.OBX(" + obxIndex + ")";
                String obx3 = terser.get(obxPath + "-3-1");

                if (obx3 == null)
                    break;

                Observation observation = new Observation();
                observation.setId(UUID.randomUUID().toString());
                if (context.getPatientId() != null) {
                    observation.setSubject(new Reference("Patient/" + context.getPatientId()));
                }
                observation.setStatus(Observation.ObservationStatus.FINAL);

                String obx3Text = terser.get(obxPath + "-3-2");
                CodeableConcept code = new CodeableConcept();
                code.addCoding().setSystem(MappingConstants.SYSTEM_LOINC).setCode(obx3).setDisplay(obx3Text);
                observation.setCode(code);

                String value = terser.get(obxPath + "-5-1");
                String units = terser.get(obxPath + "-6-1");

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

                String status = terser.get(obxPath + "-11");
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
                String interpretation = terser.get(obxPath + "-8");
                if (interpretation != null) {
                    observation.addInterpretation().addCoding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                            .setCode(interpretation);
                }

                // OBX-14 Date/Time of the Observation
                String effectiveDateStr = terser.get(obxPath + "-14");
                if (effectiveDateStr != null && !effectiveDateStr.isEmpty()) {
                    try {
                        Date date = Date.from(DateTimeUtil.parseHl7DateTime(effectiveDateStr).toInstant());
                        if (date != null) {
                            observation.setEffective(new DateTimeType(date));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse observation effective date: {}", effectiveDateStr);
                    }
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
