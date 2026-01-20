package com.al.fhirhl7transformer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for conversion results supporting partial success.
 * Contains the converted FHIR Bundle along with any errors or warnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResult {

    /**
     * The converted FHIR Bundle (may be partial if errors occurred)
     */
    private Bundle bundle;

    /**
     * List of errors that occurred during conversion
     */
    @Builder.Default
    private List<ConversionError> errors = new ArrayList<>();

    /**
     * List of warnings (non-fatal issues)
     */
    @Builder.Default
    private List<ConversionError> warnings = new ArrayList<>();

    /**
     * FHIR OperationOutcome for detailed error reporting
     */
    private OperationOutcome operationOutcome;

    /**
     * Whether any errors occurred
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Whether any warnings occurred
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Whether the conversion was a partial success (has bundle but also errors)
     */
    public boolean isPartialSuccess() {
        return bundle != null && hasErrors();
    }

    /**
     * Whether the conversion was fully successful (no errors)
     */
    public boolean isFullSuccess() {
        return bundle != null && !hasErrors();
    }

    /**
     * Add an error to the result
     */
    public void addError(ConversionError error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }

    /**
     * Add a warning to the result
     */
    public void addWarning(ConversionError warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }

    /**
     * Get total count of issues (errors + warnings)
     */
    public int getTotalIssueCount() {
        int count = 0;
        if (errors != null)
            count += errors.size();
        if (warnings != null)
            count += warnings.size();
        return count;
    }

    /**
     * Create a successful result with no errors
     */
    public static ConversionResult success(Bundle bundle) {
        return ConversionResult.builder()
                .bundle(bundle)
                .build();
    }

    /**
     * Create a failed result with an error message
     */
    public static ConversionResult failure(String errorMessage) {
        ConversionResult result = new ConversionResult();
        result.addError(ConversionError.builder()
                .message(errorMessage)
                .severity(ConversionError.Severity.ERROR)
                .errorCode("CONVERSION_FAILED")
                .build());
        return result;
    }
}
