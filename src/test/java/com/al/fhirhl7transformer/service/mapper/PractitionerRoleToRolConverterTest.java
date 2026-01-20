package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PractitionerRoleToRolConverterTest {

    @Test
    public void testConvert() throws Exception {
        PractitionerRoleToRolConverter converter = new PractitionerRoleToRolConverter();
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");
        Terser terser = new Terser(adt);

        PractitionerRole pr = new PractitionerRole();
        pr.addIdentifier().setValue("PR-123").setSystem("urn:id");
        pr.addCode().addCoding().setCode("DOC").setDisplay("Doctor");
        pr.setPractitioner(new Reference("Practitioner/P-999").setDisplay("Doe, John"));
        pr.setOrganization(new Reference("Organization/O-111").setDisplay("Hospital A"));
        pr.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("555-1212")
                .setUse(ContactPoint.ContactPointUse.WORK);

        converter.convert(pr, adt, terser);

        // Verify ROL segment
        String rol1 = terser.get("/.ROL(0)-1-1");
        assertEquals("PR-123", rol1);

        String roleCode = terser.get("/.ROL(0)-3-1");
        assertEquals("DOC", roleCode);

        String family = terser.get("/.ROL(0)-4-2");
        assertEquals("Doe", family);
        String given = terser.get("/.ROL(0)-4-3");
        assertEquals("John", given);

        String org = terser.get("/.ROL(0)-10-1");
        assertEquals("Hospital A", org);

        String phone = terser.get("/.ROL(0)-12(0)-1");
        assertEquals("555-1212", phone);
    }
}
