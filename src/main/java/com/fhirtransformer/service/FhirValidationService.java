package com.fhirtransformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

@Service
public class FhirValidationService {

    private final FhirValidator validator;

    public FhirValidationService() {
        FhirContext fhirContext = FhirContext.forR4();
        this.validator = fhirContext.newValidator();
        // You can add more validation modules here if needed (e.g., Schema, Schematron)
        // For now, we use the default validation support
    }

    public ValidationResult validate(IBaseResource resource) {
        return validator.validateWithResult(resource);
    }

    public void validateAndThrow(IBaseResource resource) {
        ValidationResult result = validate(resource);
        if (!result.isSuccessful()) {
            throw new RuntimeException("FHIR Validation Failed: " + result.getMessages().toString());
        }
    }
}
