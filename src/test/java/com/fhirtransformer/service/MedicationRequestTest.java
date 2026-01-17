package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ca.uhn.fhir.validation.ValidationResult;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MedicationRequestTest {

    private Hl7ToFhirService hl7ToFhirService;

    @BeforeEach
    public void setUp() {
        HapiContext hapiContext = new DefaultHapiContext();
        FhirContext fhirContext = FhirContext.forR4();
        FhirValidationService fhirValidationService = Mockito.mock(FhirValidationService.class);
        ValidationResult validationResult = mock(ValidationResult.class);
        when(validationResult.isSuccessful()).thenReturn(true);
        when(fhirValidationService.validate(any())).thenReturn(validationResult);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        // Ensure validation service returns true/valid if needed, though for conversion
        // it might not be strictly required depending on logic
        // But checking the code, convertHl7ToFhir doesn't seem to block on validation
        // failure unless constructed so.

        hl7ToFhirService = new Hl7ToFhirService(fhirValidationService, fhirContext, hapiContext, meterRegistry);
    }

    @Test
    public void testMedicationConversion() throws Exception {
        // HL7 with RXE segment
        String hl7 = "MSH|^~\\&|HIS|RIH|EKG|EkG|199904140038||ADT^A01|MSG001|P|2.5\r" +
                "PID|1||104||MEDICATION^TEST||19900101|M\r" +
                "RXE|1|RX12345^Aspirin 81mg|81||mg||Take with food|||30|tablets|3";

        String fhir = hl7ToFhirService.convertHl7ToFhir(hl7);

        System.out.println("Medication Test Result: " + fhir);
        assertNotNull(fhir);
        assertTrue(fhir.contains("MedicationRequest"), "Should contain MedicationRequest resource");
        assertTrue(fhir.contains("RX12345"), "Should contain Medication Code");
        assertTrue(fhir.contains("Aspirin 81mg"), "Should contain Medication Name");
        assertTrue(fhir.contains("\"value\": 81"), "Should contain Dose Amount");
        assertTrue(fhir.contains("Take with food"), "Should contain Instructions");
        assertTrue(fhir.contains("\"value\": 30"), "Should contain Dispense Amount");
        assertTrue(fhir.contains("\"numberOfRepeatsAllowed\": 3"), "Should contain Refills");
    }
}
