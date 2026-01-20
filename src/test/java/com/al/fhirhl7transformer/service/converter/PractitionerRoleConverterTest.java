package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PractitionerRoleConverterTest {

    @Test
    public void testConvert() throws Exception {
        PractitionerRoleConverter converter = new PractitionerRoleConverter();
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");
        Terser terser = new Terser(adt);

        // Populate ROL segment (Note: ADT_A01 in v2.5 has ROL at root, usually
        // repeating)
        terser.set("/.ROL(0)-1-1", "ROL-123");
        terser.set("/.ROL(0)-3-1", "CP"); // Consulting Provider
        terser.set("/.ROL(0)-4-2", "Doe");
        terser.set("/.ROL(0)-4-3", "Jane");
        terser.set("/.ROL(0)-12-1", "555-9999");
        terser.set("/.ROL(0)-12-2", "WPN");

        Bundle bundle = new Bundle();
        ConversionContext context = ConversionContext.builder().build();

        List<PractitionerRole> results = converter.convert(terser, bundle, context);

        assertFalse(results.isEmpty());
        PractitionerRole pr = results.get(0);

        assertEquals("ROL-123", pr.getIdentifierFirstRep().getValue());
        assertEquals("CP", pr.getCodeFirstRep().getCodingFirstRep().getCode());
        assertEquals("Consulting", pr.getCodeFirstRep().getCodingFirstRep().getDisplay());

        assertEquals("Jane Doe", pr.getPractitioner().getDisplay());

        assertEquals("555-9999", pr.getTelecomFirstRep().getValue());
    }
}
