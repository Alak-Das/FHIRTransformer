package com.fhirtransformer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for batch conversion operations.
 * 
 * @author FHIR Transformer Team
 * @version 1.1.0
 * @since 1.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchConversionResponse {

    /**
     * Total number of messages in the batch.
     */
    private int totalMessages;

    /**
     * Number of successfully converted messages.
     */
    private int successCount;

    /**
     * Number of failed conversions.
     */
    private int failureCount;

    /**
     * List of successful conversion results.
     */
    private List<ConversionResult> results = new ArrayList<>();

    /**
     * List of errors for failed conversions.
     */
    private List<ConversionError> errors = new ArrayList<>();

    /**
     * Total processing time in milliseconds.
     */
    private long processingTimeMs;

    /**
     * Individual conversion result.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionResult {
        /**
         * Index of the message in the batch (0-based).
         */
        private int index;

        /**
         * Converted output (FHIR JSON or HL7 message).
         */
        private String output;

        /**
         * Processing time for this message in milliseconds.
         */
        private long processingTimeMs;

        /**
         * Message ID or transaction ID.
         */
        private String messageId;
    }

    /**
     * Conversion error details.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionError {
        /**
         * Index of the failed message in the batch (0-based).
         */
        private int index;

        /**
         * Error message.
         */
        private String error;

        /**
         * Original input that failed (truncated if too long).
         */
        private String input;
    }
}
