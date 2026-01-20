package com.al.fhirhl7transformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import com.al.fhirhl7transformer.exception.FhirValidationException;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FhirValidationService {

    private final FhirValidator validator;

    public FhirValidationService(FhirContext fhirContext) {
        this.validator = fhirContext.newValidator();

        // Setup terminology validation support
        ValidationSupportChain supportChain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(fhirContext),
                new InMemoryTerminologyServerValidationSupport(fhirContext),
                new CommonCodeSystemsTerminologyService(fhirContext));

        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(supportChain);
        this.validator.registerValidatorModule(instanceValidator);
    }

    public ValidationResult validate(IBaseResource resource) {
        return validator.validateWithResult(resource);
    }

    public void validateAndThrow(IBaseResource resource) {
        ValidationResult result = validate(resource);
        if (!result.isSuccessful()) {
            List<FhirValidationException.ValidationError> errors = result.getMessages().stream()
                    .filter(msg -> msg.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR
                            || msg.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.FATAL)
                    .map(msg -> new FhirValidationException.ValidationError(
                            msg.getSeverity().name(),
                            msg.getLocationString() != null ? msg.getLocationString() : "Unknown",
                            msg.getMessage()))
                    .collect(Collectors.toList());

            if (!errors.isEmpty()) {
                String summary = String.format("FHIR validation failed with %d error(s)", errors.size());
                throw new FhirValidationException(summary, errors);
            }
        }
    }

    public String getValidationErrorSummary(ValidationResult result) {
        if (result.isSuccessful()) {
            return "Validation successful";
        }

        return result.getMessages().stream()
                .filter(msg -> msg.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR
                        || msg.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.FATAL)
                .map(msg -> String.format("[%s] %s: %s",
                        msg.getSeverity(),
                        msg.getLocationString() != null ? msg.getLocationString() : "Unknown",
                        msg.getMessage()))
                .collect(Collectors.joining("; "));
    }
}
