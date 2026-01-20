package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Procedure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProcedureToPr1ConverterTest {

    @Test
    public void testConvertMultipleProcedures() throws Exception {
        ProcedureToPr1Converter converter = new ProcedureToPr1Converter();
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");
        Terser terser = new Terser(adt);

        Procedure p1 = new Procedure();
        p1.setCode(new org.hl7.fhir.r4.model.CodeableConcept()
                .addCoding(new org.hl7.fhir.r4.model.Coding().setCode("P1")));

        Procedure p2 = new Procedure();
        p2.setCode(new org.hl7.fhir.r4.model.CodeableConcept()
                .addCoding(new org.hl7.fhir.r4.model.Coding().setCode("P2")));

        // Simulate adding GT1 and IN1 BEFORE Procedure (as happens in the integration
        // test loop)
        adt.getGT1(0).getGuarantorName(0).getFamilyName().getSurname().setValue("GT1_TEST");
        adt.getINSURANCE(0).getIN1().getInsuranceCompanyID(0).getIDNumber().setValue("IN1_TEST");

        converter.convert(p1, adt, terser);
        converter.convert(p2, adt, terser);

        assertEquals("P1", adt.getPROCEDURE(0).getPR1().getProcedureCode().getIdentifier().getValue());
        assertEquals("P2", adt.getPROCEDURE(1).getPR1().getProcedureCode().getIdentifier().getValue());

        String encoded = new ca.uhn.hl7v2.DefaultHapiContext().getPipeParser().encode(adt);
        System.out.println("Encoded: " + encoded);
        if (!encoded.contains("PR1|1||P1") || !encoded.contains("PR1|2||P2")) {
            throw new RuntimeException("PR1 segment missing from encoded message: " + encoded);
        }
    }
}
