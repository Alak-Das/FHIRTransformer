package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.model.v25.message.ORM_O01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Coding;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CarePlanToOrcConverterTest {

    @Test
    public void testConvert() throws Exception {
        CarePlanToOrcConverter converter = new CarePlanToOrcConverter();
        ORM_O01 orm = new ORM_O01();
        orm.initQuickstart("ORM", "O01", "P");
        Terser terser = new Terser(orm);

        CarePlan carePlan = new CarePlan();
        carePlan.setStatus(CarePlan.CarePlanStatus.ACTIVE);
        carePlan.addIdentifier().setValue("P-ORD-111")
                .setType(new org.hl7.fhir.r4.model.CodeableConcept().addCoding(new Coding().setCode("PLAC")));
        carePlan.setCreated(new Date());

        converter.convert(carePlan, orm, terser);

        // ORC-1 Link to Status (Active -> NW New Order or IP In Process depending on
        // mapping context)
        // Checking implementation: Active -> NW in ORC-1
        assertEquals("NW", terser.get("/.ORC(0)-1"));

        // ORC-2 Placer Order
        assertEquals("P-ORD-111", terser.get("/.ORC(0)-2-1"));

        // ORC-5 Status
        assertEquals("IP", terser.get("/.ORC(0)-5"));
    }
}
