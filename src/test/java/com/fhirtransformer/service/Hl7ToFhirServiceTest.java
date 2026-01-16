package com.fhirtransformer.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class Hl7ToFhirServiceTest {

    @Autowired
    private Hl7ToFhirService hl7ToFhirService;

    @Test
    public void testConversion() throws Exception {
        // Valid HL7 v2.5 Message
        // MSH-9: ADT^A01, MSH-10: 1001, MSH-11: P, MSH-12: 2.5
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1001|P|2.5\r" +
                "PID|1||100||DOE^JOHN||19700101|M||||||||||1000\r" +
                "PV1|1|I|2000^2012^01||||002970^FUSILIER^KAMERA^^^MD^Dr||||||||| |||||||||||||||||||||||||20250101000100";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println(fhir);
        assertNotNull(fhir);
        assertTrue(fhir.contains("\"resourceType\": \"Bundle\""));
        assertTrue(fhir.contains("\"family\": \"DOE\""));
        assertTrue(fhir.contains("\"gender\": \"male\""));
    }

    @Test
    public void testZSegmentConversion() throws Exception {
        // HL7 with ZPI segment
        // MSH-9: ADT^A01 (Triggers CustomADT_A01)
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|1002|P|2.5\r" +
                "PID|1||100||DOE^JANE||19700101|F\r" +
                "ZPI|1|Fluffy|VIP-Gold|Active"; // Custom ZPI Segment

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("Z-Segment Test Result: " + fhir);
        assertNotNull(fhir);
        assertTrue(fhir.contains("Fluffy"), "Should contain Pet Name from ZPI-2");
        assertTrue(fhir.contains("VIP-Gold"), "Should contain VIP Level from ZPI-3");
        assertTrue(fhir.contains("http://example.org/fhir/StructureDefinition/pet-name"),
                "Should contain extension URL");
    }
}
