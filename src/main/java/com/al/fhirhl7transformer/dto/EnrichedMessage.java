package com.al.fhirhl7transformer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnrichedMessage {
    private String content; // The actual message payload (HL7 or FHIR JSON)
    private String transactionId;
}
