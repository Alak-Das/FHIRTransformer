package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@Component
public class CoverageToIn1Converter implements FhirToHl7Converter<Coverage> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Coverage;
    }

    @Override
    public void convert(Coverage coverage, Message message, Terser terser) throws HL7Exception {
        if (!(message instanceof ADT_A01)) {
            return;
        }

        ADT_A01 adt = (ADT_A01) message;
        int in1Index = adt.getINSURANCEReps();
        // Force creation
        ca.uhn.hl7v2.model.v25.segment.IN1 in1 = adt.getINSURANCE(in1Index).getIN1();

        // IN1-1 Set ID
        in1.getSetIDIN1().setValue(String.valueOf(in1Index + 1));

        // IN1-36 Policy Number / Subscriber ID
        if (coverage.hasSubscriberId()) {
            in1.getPolicyNumber().setValue(coverage.getSubscriberId());
        }

        // IN1-2 Insurance Plan ID
        if (coverage.hasIdentifier()) {
            in1.getInsurancePlanID().getIdentifier().setValue(coverage.getIdentifierFirstRep().getValue());
        }

        // IN1-3 Company ID / Payor
        if (coverage.hasPayor()) {
            org.hl7.fhir.r4.model.Reference payor = coverage.getPayorFirstRep();
            if (payor.hasReference()) {
                String id = payor.getReference();
                if (id.contains("/"))
                    id = id.substring(id.lastIndexOf("/") + 1);
                in1.getInsuranceCompanyID(0).getIDNumber().setValue(id);
            }
            if (payor.hasDisplay()) {
                in1.getInsuranceCompanyName(0).getOrganizationName().setValue(payor.getDisplay());
            }
        }

        if (coverage.hasPeriod()) {
            if (coverage.getPeriod().hasStart()) {
                in1.getPlanEffectiveDate().setValue(
                        new SimpleDateFormat("yyyyMMdd").format(coverage.getPeriod().getStart()));
            }
            if (coverage.getPeriod().hasEnd()) {
                in1.getPlanExpirationDate().setValue(
                        new SimpleDateFormat("yyyyMMdd").format(coverage.getPeriod().getEnd()));
            }
        }

        // IN1-17 Relationship to Insured
        if (coverage.hasRelationship()) {
            in1.getInsuredSRelationshipToPatient().getIdentifier()
                    .setValue(coverage.getRelationship().getCodingFirstRep().getCode());
        }

        // IN1-47 Plan Type
        if (coverage.hasType() && coverage.getType().hasCoding()) {
            in1.getCoverageType().setValue(coverage.getType().getCodingFirstRep().getCode());
        }
    }
}
