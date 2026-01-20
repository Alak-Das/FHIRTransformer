package com.al.fhirhl7transformer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an error that occurred during HL7 to FHIR conversion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionError {

    /**
     * The segment where the error occurred (e.g., "PID", "OBX", "PV1")
     */
    private String segment;

    /**
     * The segment index (for repeating segments like OBX)
     */
    private int segmentIndex;

    /**
     * The field number where the error occurred (if applicable)
     */
    private String field;

    /**
     * Error code for programmatic handling
     */
    private String errorCode;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Severity: ERROR, WARNING, INFORMATION
     */
    private Severity severity;

    /**
     * The original exception class name (for debugging)
     */
    private String exceptionType;

    public enum Severity {
        ERROR,
        WARNING,
        INFORMATION
    }

    public static ConversionError segmentError(String segment, int index, String message) {
        return ConversionError.builder()
                .segment(segment)
                .segmentIndex(index)
                .message(message)
                .severity(Severity.ERROR)
                .errorCode("SEGMENT_ERROR")
                .build();
    }

    public static ConversionError fieldError(String segment, String field, String message) {
        return ConversionError.builder()
                .segment(segment)
                .field(field)
                .message(message)
                .severity(Severity.ERROR)
                .errorCode("FIELD_ERROR")
                .build();
    }

    public static ConversionError warning(String segment, String message) {
        return ConversionError.builder()
                .segment(segment)
                .message(message)
                .severity(Severity.WARNING)
                .errorCode("WARNING")
                .build();
    }
}
