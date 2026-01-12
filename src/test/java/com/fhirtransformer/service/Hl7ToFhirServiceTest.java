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
}
