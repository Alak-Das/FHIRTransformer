package com.al.fhirhl7transformer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch HL7 to FHIR conversion.
 * 
 * @author FHIR Transformer Team
 * @version 1.1.0
 * @since 1.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchHl7Request {

    /**
     * List of HL7 v2.5 messages to convert.
     * Maximum 100 messages per batch to prevent memory issues.
     */
    @NotEmpty(message = "Messages list cannot be empty")
    @Size(min = 1, max = 100, message = "Batch size must be between 1 and 100 messages")
    private List<String> messages;

    /**
     * Optional tenant ID for multi-tenant scenarios.
     */
    private String tenantId;
}
