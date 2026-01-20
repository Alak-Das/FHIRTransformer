package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.segment.DG1;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

@Component
public class ConditionToDg1Converter implements FhirToHl7Converter<Condition> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Condition;
    }

    @Override
    public void convert(Condition cond, Message message, Terser terser) throws HL7Exception {
        if (!(message instanceof ADT_A01)) {
            return;
        }

        ADT_A01 adt = (ADT_A01) message;
        int dg1Index = adt.getDG1Reps();
        DG1 dg1 = adt.getDG1(dg1Index);

        // DG1-1 Set ID
        dg1.getSetIDDG1().setValue(String.valueOf(dg1Index + 1));

        // DG1-3 Diagnosis Code
        if (cond.hasCode() && cond.getCode().hasCoding()) {
            dg1.getDiagnosisCodeDG1().getIdentifier()
                    .setValue(cond.getCode().getCodingFirstRep().getCode());
            dg1.getDiagnosisCodeDG1().getText().setValue(cond.getCode().getCodingFirstRep().getDisplay());
            dg1.getDiagnosisCodeDG1().getNameOfCodingSystem().setValue("ICD-10");
        }

        // DG1-6 Diagnosis Type
        if (cond.hasCategory()) {
            dg1.getDiagnosisType().setValue(cond.getCategoryFirstRep().getCodingFirstRep().getDisplay());
        }
    }
}
