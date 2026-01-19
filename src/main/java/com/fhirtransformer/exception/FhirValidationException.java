package com.fhirtransformer.exception;

import lombok.Getter;

import java.util.List;

/**
 * Exception thrown when FHIR resource validation fails.
 * Contains detailed validation error messages for client debugging.
 */
@Getter
public class FhirValidationException extends RuntimeException {

    private final List<ValidationError> validationErrors;

    public FhirValidationException(String message, List<ValidationError> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    /**
     * Represents a single validation error with location and severity
     */
    @Getter
    public static class ValidationError {
        private final String severity;
        private final String location;
        private final String message;

        public ValidationError(String severity, String location, String message) {
            this.severity = severity;
            this.location = location;
            this.message = message;
        }
    }
}
