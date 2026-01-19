package com.al.fhirhl7transformer.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class FhirToHl7ServiceTest {

    @Autowired
    private FhirToHl7Service fhirToHl7Service;

    @Test
    public void testConversion() throws Exception {
        String fhirJson = "{" +
                "\"resourceType\": \"Bundle\"," +
                "\"type\": \"transaction\"," +
                "\"entry\": [" +
                "  {" +
                "    \"resource\": {" +
                "      \"resourceType\": \"Patient\"," +
                "      \"identifier\": [" +
                "        { \"value\": \"12345\" }" +
                "      ]," +
                "      \"name\": [" +
                "        { \"family\": \"SMITH\", \"given\": [\"JOHN\"] }" +
                "      ]," +
                "      \"gender\": \"male\"" +
                "    }" +
                "  }," +
                "  {" +
                "    \"resource\": {" +
                "      \"resourceType\": \"Encounter\"," +
                "      \"identifier\": [" +
                "        { \"value\": \"VISIT-001\" }" +
                "      ]," +
                "      \"class\": {" +
                "        \"code\": \"I\"" +
                "      }" +
                "    }" +
                "  }" +
                "]" +
                "}";

        String hl7 = fhirToHl7Service.convertFhirToHl7(fhirJson);

        System.out.println(hl7);
        assertNotNull(hl7);
        assertTrue(hl7.contains("MSH|^~\\&")); // Check for MSH
        assertTrue(hl7.contains("PID|"));
        assertTrue(hl7.contains("|12345|")); // PID-3
        assertTrue(hl7.contains("SMITH^JOHN"), "HL7 Message: " + hl7); // PID-5
        assertTrue(hl7.contains("|M"), "HL7 Message: " + hl7); // Gender
        assertTrue(hl7.contains("PV1|"), "HL7 Message: " + hl7);
        assertTrue(hl7.contains("|VISIT-001"), "HL7 Message: " + hl7); // PV1
    }
}
