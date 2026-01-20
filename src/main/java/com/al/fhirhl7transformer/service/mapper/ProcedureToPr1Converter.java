package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProcedureToPr1Converter implements FhirToHl7Converter<Procedure> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Procedure;
    }

    @Override
    public void convert(Procedure procedure, Message message, Terser terser) throws HL7Exception {
        if (!(message instanceof ADT_A01)) {
            return;
        }
        ADT_A01 adt = (ADT_A01) message;

        // Check if there is an existing empty PROCEDURE group we can reuse
        // This handles cases where an empty group might be implicitly created
        int targetIndex = -1;
        int reps = adt.getPROCEDUREReps();
        for (int i = 0; i < reps; i++) {
            if (adt.getPROCEDURE(i).getPR1().isEmpty()) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            targetIndex = reps;
        }

        ca.uhn.hl7v2.model.v25.segment.PR1 pr1 = adt.getPROCEDURE(targetIndex).getPR1();
        pr1.getSetIDPR1().setValue(String.valueOf(targetIndex + 1));

        // PR1-3 Procedure Code
        if (procedure.hasCode() && procedure.getCode().hasCoding()) {
            pr1.getProcedureCode().getIdentifier().setValue(procedure.getCode().getCodingFirstRep().getCode());
            pr1.getProcedureCode().getText().setValue(procedure.getCode().getCodingFirstRep().getDisplay());
        }

        // PR1-5 Procedure Date/Time
        if (procedure.hasPerformedDateTimeType()) {
            pr1.getProcedureDateTime().getTime().setValue(
                    new SimpleDateFormat("yyyyMMddHHmm")
                            .format(procedure.getPerformedDateTimeType().getValue()));
        }
    }
}
