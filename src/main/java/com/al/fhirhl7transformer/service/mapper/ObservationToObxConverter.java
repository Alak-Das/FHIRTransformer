package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.datatype.NM;
import ca.uhn.hl7v2.model.v25.datatype.ST;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.segment.OBX;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@Component
public class ObservationToObxConverter implements FhirToHl7Converter<Observation> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Observation;
    }

    @Override
    public void convert(Observation obs, Message message, Terser terser) throws HL7Exception {
        if (!(message instanceof ADT_A01)) {
            // For now, only ADT A01 is supported for this specific segment mapping location
            // In future, if ORU^R01 is supported, we would need to handle its different
            // structure (Groups)
            return;
        }

        ADT_A01 adt = (ADT_A01) message;
        int obxIndex = adt.getOBXReps();
        OBX obx = adt.getOBX(obxIndex);

        // OBX-1 Set ID
        obx.getSetIDOBX().setValue(String.valueOf(obxIndex + 1));

        // Check if it is a Note (LOINC 34109-9)
        boolean isNote = obs.hasCode() && obs.getCode().hasCoding() &&
                "34109-9".equals(obs.getCode().getCodingFirstRep().getCode());

        if (isNote) {
            obx.getValueType().setValue("TX"); // Text
            obx.getObservationIdentifier().getIdentifier().setValue("34109-9");
            obx.getObservationIdentifier().getText().setValue("Note");
            if (obs.hasValueStringType()) {
                obx.getObservationValue(0).getData().parse(obs.getValueStringType().getValue());
            }
        } else {
            // Standard OBX Mapping
            if (obs.hasValueQuantity()) {
                obx.getValueType().setValue("NM"); // Numeric
                NM nm = new NM(adt);
                nm.setValue(obs.getValueQuantity().getValue().toString());
                obx.getObservationValue(0).setData(nm);

                if (obs.getValueQuantity().hasUnit())
                    obx.getUnits().getIdentifier().setValue(obs.getValueQuantity().getUnit());
                else if (obs.getValueQuantity().hasCode())
                    obx.getUnits().getIdentifier().setValue(obs.getValueQuantity().getCode());
            } else if (obs.hasValueStringType()) {
                obx.getValueType().setValue("ST"); // String
                ST st = new ST(adt);
                st.setValue(obs.getValueStringType().getValue());
                obx.getObservationValue(0).setData(st);
            } else if (obs.hasValueCodeableConcept()) {
                obx.getValueType().setValue("CE"); // Coded Element
                terser.set("/.OBX(" + obxIndex + ")-5-1",
                        obs.getValueCodeableConcept().getCodingFirstRep().getCode());
                terser.set("/.OBX(" + obxIndex + ")-5-2",
                        obs.getValueCodeableConcept().getCodingFirstRep().getDisplay());
            }

            // OBX-3 Observation Identifier
            if (obs.hasCode() && obs.getCode().hasCoding()) {
                obx.getObservationIdentifier().getIdentifier()
                        .setValue(obs.getCode().getCodingFirstRep().getCode());
                obx.getObservationIdentifier().getText()
                        .setValue(obs.getCode().getCodingFirstRep().getDisplay());
                obx.getObservationIdentifier().getNameOfCodingSystem().setValue("LN");
            }
        }

        // OBX-8 Interpretation
        if (obs.hasInterpretation()) {
            obx.getAbnormalFlags(0).setValue(obs.getInterpretationFirstRep().getCodingFirstRep().getCode());
        }

        // OBX-14 Effective Date/Time
        if (obs.hasEffectiveDateTimeType()) {
            obx.getDateTimeOfTheObservation().getTime().setValue(
                    new SimpleDateFormat("yyyyMMddHHmm").format(obs.getEffectiveDateTimeType().getValue()));
        }

        // OBX-11 Status
        if (obs.hasStatus()) {
            switch (obs.getStatus()) {
                case FINAL:
                    obx.getObservationResultStatus().setValue("F");
                    break;
                case PRELIMINARY:
                    obx.getObservationResultStatus().setValue("P");
                    break;
                case AMENDED:
                    obx.getObservationResultStatus().setValue("C");
                    break;
                case ENTEREDINERROR:
                    obx.getObservationResultStatus().setValue("W");
                    break;
                case CANCELLED:
                    obx.getObservationResultStatus().setValue("X");
                    break;
                default:
                    obx.getObservationResultStatus().setValue("F");
                    break;
            }
        }
    }
}
