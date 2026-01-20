package com.al.fhirhl7transformer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for HL7 parsing and FHIR conversion behavior.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.parsing")
public class ParsingConfiguration {

    /**
     * Parsing strictness level.
     * STRICT: Fail on any parsing error
     * LENIENT: Continue on non-critical errors, collect warnings
     * PERMISSIVE: Best-effort parsing, ignore most errors
     */
    private StrictnessLevel strictness = StrictnessLevel.LENIENT;

    /**
     * Whether to continue processing other segments when one fails.
     * Only applicable when strictness is not STRICT.
     */
    private boolean continueOnError = true;

    /**
     * Whether to validate output FHIR resources.
     */
    private boolean validationEnabled = true;

    /**
     * Whether to fail the conversion if FHIR validation produces warnings.
     */
    private boolean failOnValidationWarning = false;

    /**
     * Whether to include OperationOutcome in the response bundle.
     */
    private boolean includeOperationOutcome = true;

    /**
     * Maximum number of errors to collect before stopping.
     * 0 = unlimited
     */
    private int maxErrors = 50;

    /**
     * Whether to include the original HL7 segment text in error messages.
     */
    private boolean includeSegmentTextInErrors = false;

    public enum StrictnessLevel {
        /**
         * Fail immediately on any error
         */
        STRICT,

        /**
         * Continue on non-critical errors, collect warnings
         */
        LENIENT,

        /**
         * Best-effort parsing, ignore most errors
         */
        PERMISSIVE
    }

    /**
     * Check if the current strictness allows continuing after an error
     */
    public boolean shouldContinueOnError() {
        return strictness != StrictnessLevel.STRICT && continueOnError;
    }

    /**
     * Check if we should treat a validation warning as an error
     */
    public boolean treatWarningAsError() {
        return strictness == StrictnessLevel.STRICT && failOnValidationWarning;
    }
}
