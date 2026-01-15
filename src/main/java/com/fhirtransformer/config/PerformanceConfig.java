package com.fhirtransformer.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Performance optimization: Create singleton FHIR and HL7 contexts
 * These are thread-safe and expensive to create, so we reuse them
 */
@Configuration
public class PerformanceConfig {

    /**
     * Singleton FHIR R4 context - thread-safe and reusable
     * Creating FhirContext is expensive (~1-2 seconds), so we create it once
     */
    @Bean
    public FhirContext fhirContext() {
        FhirContext ctx = FhirContext.forR4();
        // Performance optimization: disable validation for faster parsing
        ctx.getParserOptions().setStripVersionsFromReferences(false);
        ctx.getParserOptions().setOverrideResourceIdWithBundleEntryFullUrl(false);
        return ctx;
    }

    /**
     * Singleton HL7 v2 context - thread-safe and reusable
     * Creating HapiContext is expensive, so we create it once
     */
    @Bean
    public HapiContext hapiContext() {
        DefaultHapiContext ctx = new DefaultHapiContext();
        // Disable validation for better performance with real-world messages
        ctx.setValidationContext(new ca.uhn.hl7v2.validation.impl.NoValidation());
        return ctx;
    }
}
