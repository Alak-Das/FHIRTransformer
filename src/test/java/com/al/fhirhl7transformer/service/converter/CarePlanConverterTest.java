package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.model.v25.message.ORM_O01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CarePlanConverterTest {

    @Test
    public void testConvert() throws Exception {
        CarePlanConverter converter = new CarePlanConverter();
        ORM_O01 orm = new ORM_O01();
        orm.initQuickstart("ORM", "O01", "P");
        Terser terser = new Terser(orm);

        // Populate ORC
        terser.set("/.ORC(0)-1", "NW"); // New Order
        terser.set("/.ORC(0)-2-1", "PLAC-111");
        terser.set("/.ORC(0)-7-4", "20230101");
        terser.set("/.ORC(0)-9", "20230101120000"); // Tran Date

        Bundle bundle = new Bundle();
        ConversionContext context = ConversionContext.builder().patientId("PAT-123").build();

        List<CarePlan> results = converter.convert(terser, bundle, context);

        assertFalse(results.isEmpty());
        CarePlan cp = results.get(0);

        assertEquals(CarePlan.CarePlanStatus.ACTIVE, cp.getStatus());
        assertEquals(CarePlan.CarePlanIntent.ORDER, cp.getIntent());

        assertEquals("PLAC-111", cp.getIdentifierFirstRep().getValue());
        assertNotNull(cp.getCreated());

        assertEquals("Patient/PAT-123", cp.getSubject().getReference());
    }
}
