package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@Component
public class RelatedPersonToGt1Converter implements FhirToHl7Converter<RelatedPerson> {

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof RelatedPerson;
    }

    @Override
    public void convert(RelatedPerson rp, Message message, Terser terser) throws HL7Exception {
        if (!(message instanceof ADT_A01)) {
            return;
        }

        ADT_A01 adt = (ADT_A01) message;
        int gt1Index = adt.getGT1Reps();
        // Force creation
        adt.getGT1(gt1Index);

        String gt1Path = "/.GT1(" + gt1Index + ")";

        // GT1-1 Set ID
        terser.set(gt1Path + "-1", String.valueOf(gt1Index + 1));

        // GT1-3 Name
        if (rp.hasName()) {
            HumanName name = rp.getNameFirstRep();
            if (name.hasFamily())
                terser.set(gt1Path + "-3-1", name.getFamily());
            if (name.hasGiven())
                terser.set(gt1Path + "-3-2", name.getGivenAsSingleString());
        }

        // GT1-5 Address
        if (rp.hasAddress()) {
            Address addr = rp.getAddressFirstRep();
            if (addr.hasLine())
                terser.set(gt1Path + "-5-1", addr.getLine().get(0).getValue());
            if (addr.hasCity())
                terser.set(gt1Path + "-5-3", addr.getCity());
            if (addr.hasState())
                terser.set(gt1Path + "-5-4", addr.getState());
            if (addr.hasPostalCode())
                terser.set(gt1Path + "-5-5", addr.getPostalCode());
        }

        // GT1-6 Phone
        if (rp.hasTelecom()) {
            terser.set(gt1Path + "-6-1", rp.getTelecomFirstRep().getValue());
        }

        // GT1-8 DOB
        if (rp.hasBirthDate()) {
            terser.set(gt1Path + "-8", new SimpleDateFormat("yyyyMMdd").format(rp.getBirthDate()));
        }

        // GT1-11 Relationship
        if (rp.hasRelationship()) {
            Coding code = rp.getRelationshipFirstRep().getCodingFirstRep();
            if (code.hasCode())
                terser.set(gt1Path + "-11-1", code.getCode());
            if (code.hasDisplay())
                terser.set(gt1Path + "-11-2", code.getDisplay());
        }
    }
}
