package com.al.fhirhl7transformer.util;

import com.al.fhirhl7transformer.dto.ConversionError;
import com.al.fhirhl7transformer.dto.ConversionResult;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import java.util.List;

/**
 * Utility class for building FHIR OperationOutcome resources from conversion
 * errors.
 */
public class OperationOutcomeBuilder {

    /**
     * Build an OperationOutcome from a ConversionResult
     */
    public static OperationOutcome fromConversionResult(ConversionResult result) {
        OperationOutcome outcome = new OperationOutcome();

        // Add errors
        if (result.getErrors() != null) {
            for (ConversionError error : result.getErrors()) {
                outcome.addIssue(createIssue(error));
            }
        }

        // Add warnings
        if (result.getWarnings() != null) {
            for (ConversionError warning : result.getWarnings()) {
                outcome.addIssue(createIssue(warning));
            }
        }

        return outcome;
    }

    /**
     * Build an OperationOutcome from a list of errors
     */
    public static OperationOutcome fromErrors(List<ConversionError> errors) {
        OperationOutcome outcome = new OperationOutcome();
        if (errors != null) {
            for (ConversionError error : errors) {
                outcome.addIssue(createIssue(error));
            }
        }
        return outcome;
    }

    /**
     * Build an OperationOutcome for a single error message
     */
    public static OperationOutcome fromMessage(String message, IssueSeverity severity) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(severity);
        issue.setCode(IssueType.PROCESSING);
        issue.setDiagnostics(message);
        return outcome;
    }

    /**
     * Build an OperationOutcome for a processing exception
     */
    public static OperationOutcome fromException(Exception e, String segment) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(IssueSeverity.ERROR);
        issue.setCode(IssueType.EXCEPTION);
        issue.setDiagnostics(e.getMessage());

        if (segment != null) {
            issue.addLocation("HL7 Segment: " + segment);
        }

        // Add exception type as extension
        CodeableConcept details = new CodeableConcept();
        details.setText(e.getClass().getSimpleName());
        issue.setDetails(details);

        return outcome;
    }

    /**
     * Create an OperationOutcome issue from a ConversionError
     */
    private static OperationOutcomeIssueComponent createIssue(ConversionError error) {
        OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();

        // Map severity
        issue.setSeverity(mapSeverity(error.getSeverity()));

        // Set issue type based on error code
        issue.setCode(mapIssueType(error.getErrorCode()));

        // Set diagnostic message
        issue.setDiagnostics(error.getMessage());

        // Add location information
        if (error.getSegment() != null) {
            StringBuilder location = new StringBuilder();
            location.append("Segment: ").append(error.getSegment());
            if (error.getSegmentIndex() > 0) {
                location.append("[").append(error.getSegmentIndex()).append("]");
            }
            if (error.getField() != null) {
                location.append(", Field: ").append(error.getField());
            }
            issue.addLocation(location.toString());
        }

        // Add error code as details
        if (error.getErrorCode() != null) {
            CodeableConcept details = new CodeableConcept();
            details.setText(error.getErrorCode());
            issue.setDetails(details);
        }

        return issue;
    }

    /**
     * Map ConversionError.Severity to OperationOutcome.IssueSeverity
     */
    private static IssueSeverity mapSeverity(ConversionError.Severity severity) {
        if (severity == null)
            return IssueSeverity.ERROR;
        switch (severity) {
            case ERROR:
                return IssueSeverity.ERROR;
            case WARNING:
                return IssueSeverity.WARNING;
            case INFORMATION:
                return IssueSeverity.INFORMATION;
            default:
                return IssueSeverity.ERROR;
        }
    }

    /**
     * Map error code to FHIR IssueType
     */
    private static IssueType mapIssueType(String errorCode) {
        if (errorCode == null)
            return IssueType.PROCESSING;
        switch (errorCode) {
            case "SEGMENT_ERROR":
                return IssueType.STRUCTURE;
            case "FIELD_ERROR":
                return IssueType.VALUE;
            case "VALIDATION_ERROR":
                return IssueType.INVALID;
            case "REQUIRED_FIELD_MISSING":
                return IssueType.REQUIRED;
            case "NOT_SUPPORTED":
                return IssueType.NOTSUPPORTED;
            case "WARNING":
                return IssueType.INFORMATIONAL;
            default:
                return IssueType.PROCESSING;
        }
    }
}
