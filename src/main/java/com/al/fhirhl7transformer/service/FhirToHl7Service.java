package com.al.fhirhl7transformer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.message.ORM_O01;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.message.SIU_S12;
import ca.uhn.hl7v2.model.v25.message.MDM_T02;
import ca.uhn.hl7v2.model.Message;

import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.al.fhirhl7transformer.dto.ConversionError;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced FHIR to HL7 v2 conversion service with:
 * - Input FHIR validation
 * - Graceful partial failure handling
 * - Multi-message-type support (ADT, ORM, ORU, MDM, SIU)
 */
@Service
@Slf4j
public class FhirToHl7Service {

    private final HapiContext hl7Context;
    private final FhirContext fhirContext;
    private final MeterRegistry meterRegistry;
    private final FhirValidationService validationService;
    private final java.util.List<com.al.fhirhl7transformer.service.mapper.FhirToHl7Converter<?>> converters;

    @Autowired
    public FhirToHl7Service(FhirContext fhirContext, HapiContext hapiContext, MeterRegistry meterRegistry,
            FhirValidationService validationService,
            java.util.List<com.al.fhirhl7transformer.service.mapper.FhirToHl7Converter<?>> converters) {
        this.hl7Context = hapiContext;
        this.fhirContext = fhirContext;
        this.meterRegistry = meterRegistry;
        this.validationService = validationService;
        this.converters = converters;
        log.info("FhirToHl7Service initialized with {} converters: {}", converters.size(), converters);
    }

    /**
     * Convert FHIR Bundle to HL7 v2 message with validation and partial failure
     * support.
     * 
     * @param fhirJson The FHIR Bundle as JSON
     * @return FhirToHl7Result containing the HL7 message and any errors
     */
    public FhirToHl7Result convertFhirToHl7WithResult(String fhirJson) {
        Timer.Sample sample = Timer.start(meterRegistry);
        List<ConversionError> errors = new ArrayList<>();
        List<ConversionError> warnings = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        try {
            // Pre-validate that input is a FHIR Bundle
            if (fhirJson == null || fhirJson.isBlank()) {
                throw new IllegalArgumentException("FHIR input cannot be null or empty");
            }

            // Check if the input looks like a Bundle before parsing
            if (!fhirJson.contains("\"resourceType\"") || !fhirJson.contains("\"Bundle\"")) {
                throw new IllegalArgumentException(
                        "Input must be a FHIR Bundle resource. Expected resourceType: Bundle");
            }

            // Parse FHIR JSON
            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, fhirJson);

            // Validate FHIR input (optional - collect warnings but don't fail)
            try {
                ValidationResult validationResult = validationService.validate(bundle);
                if (!validationResult.isSuccessful()) {
                    String summary = validationService.getValidationErrorSummary(validationResult);
                    warnings.add(ConversionError.builder()
                            .message("FHIR validation warnings: " + summary)
                            .severity(ConversionError.Severity.WARNING)
                            .errorCode("FHIR_VALIDATION_WARNING")
                            .build());
                    log.warn("FHIR validation warnings detected: {}", summary);
                }
            } catch (Exception e) {
                warnings.add(ConversionError.builder()
                        .message("FHIR validation could not be completed: " + e.getMessage())
                        .severity(ConversionError.Severity.WARNING)
                        .errorCode("VALIDATION_SKIPPED")
                        .build());
                log.warn("FHIR validation skipped due to error: {}", e.getMessage());
            }

            // Detect message type from bundle content
            MessageType messageType = detectMessageType(bundle);
            log.info("Detected message type: {} for bundle with {} entries", messageType, bundle.getEntry().size());

            // Create appropriate HL7 Message based on content
            Message hl7Message = createHl7Message(messageType);
            Terser terser = new Terser(hl7Message);

            // Populate MSH
            populateMsh(hl7Message, messageType, bundle);

            // Iterate through all Bundle Entries and delegate to Converters
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.hasResource()) {
                    Resource resource = entry.getResource();
                    String resourceType = resource.getResourceType().name();
                    log.debug("Processing resource type: {}", resourceType);

                    boolean converted = false;
                    for (com.al.fhirhl7transformer.service.mapper.FhirToHl7Converter<?> converter : converters) {
                        try {
                            if (converter.canConvert(resource)) {
                                // Safe cast: canConvert() ensures the resource is the correct type
                                @SuppressWarnings("unchecked")
                                com.al.fhirhl7transformer.service.mapper.FhirToHl7Converter<Resource> typedConverter = (com.al.fhirhl7transformer.service.mapper.FhirToHl7Converter<Resource>) converter;
                                typedConverter.convert(resource, hl7Message, terser);
                                converted = true;
                                successCount++;
                                log.debug("Successfully converted {} using {}", resourceType,
                                        converter.getClass().getSimpleName());
                            }
                        } catch (Exception e) {
                            log.error("Error converting {} with {}: {}", resourceType,
                                    converter.getClass().getSimpleName(), e.getMessage());
                            errors.add(ConversionError.builder()
                                    .segment(resourceType)
                                    .message("Failed to convert " + resourceType + ": " + e.getMessage())
                                    .severity(ConversionError.Severity.ERROR)
                                    .errorCode("RESOURCE_CONVERSION_ERROR")
                                    .exceptionType(e.getClass().getSimpleName())
                                    .build());
                            failCount++;
                        }
                    }

                    if (!converted) {
                        warnings.add(ConversionError.builder()
                                .segment(resourceType)
                                .message("No converter found for resource type: " + resourceType)
                                .severity(ConversionError.Severity.WARNING)
                                .errorCode("NO_CONVERTER")
                                .build());
                    }
                }
            }

            // Serialize to Pipe Delimited
            Parser parser = hl7Context.getPipeParser();
            String result = parser.encode(hl7Message);

            // Record Metrics
            String status = errors.isEmpty() ? "success" : (successCount > 0 ? "partial" : "error");
            meterRegistry.counter("fhir.conversion.count", "type", "fhir-to-v2", "status", status).increment();
            meterRegistry.counter("fhir.conversion.resources", "type", "fhir-to-v2", "outcome", "success")
                    .increment(successCount);
            meterRegistry.counter("fhir.conversion.resources", "type", "fhir-to-v2", "outcome", "failed")
                    .increment(failCount);
            sample.stop(meterRegistry.timer("fhir.conversion.time", "type", "fhir-to-v2"));

            log.info("FHIR to HL7 conversion completed: {} resources converted, {} failed, {} warnings",
                    successCount, failCount, warnings.size());

            return FhirToHl7Result.builder()
                    .hl7Message(result)
                    .messageType(messageType.name())
                    .errors(errors)
                    .warnings(warnings)
                    .successCount(successCount)
                    .failCount(failCount)
                    .build();

        } catch (Exception e) {
            log.error("FHIR to HL7 conversion failed", e);
            meterRegistry.counter("fhir.conversion.count", "type", "fhir-to-v2", "status", "error").increment();

            errors.add(ConversionError.builder()
                    .message("Conversion failed: " + e.getMessage())
                    .severity(ConversionError.Severity.ERROR)
                    .errorCode("CONVERSION_FAILED")
                    .exceptionType(e.getClass().getSimpleName())
                    .build());

            return FhirToHl7Result.builder()
                    .errors(errors)
                    .warnings(warnings)
                    .successCount(0)
                    .failCount(failCount)
                    .build();
        }
    }

    /**
     * Original method for backward compatibility - throws exception on failure.
     * Rethrows client errors (IllegalArgumentException, DataFormatException)
     * directly for proper 400 response.
     */
    public String convertFhirToHl7(String fhirJson) throws Exception {
        // Pre-validate input before calling the result method
        if (fhirJson == null || fhirJson.isBlank()) {
            throw new IllegalArgumentException("FHIR input cannot be null or empty");
        }

        // Check if the input is a Bundle resource type
        if (!fhirJson.contains("\"resourceType\"")) {
            throw new IllegalArgumentException("Input must be valid FHIR JSON with resourceType");
        }

        // More specific check - look for Bundle at the root level
        String trimmed = fhirJson.trim();
        if (trimmed.startsWith("{")) {
            // Try to find resourceType early in the JSON
            int rtIndex = trimmed.indexOf("\"resourceType\"");
            if (rtIndex != -1) {
                int colonIndex = trimmed.indexOf(":", rtIndex);
                if (colonIndex != -1) {
                    int valueStart = trimmed.indexOf("\"", colonIndex);
                    int valueEnd = trimmed.indexOf("\"", valueStart + 1);
                    if (valueStart != -1 && valueEnd != -1) {
                        String resourceType = trimmed.substring(valueStart + 1, valueEnd);
                        if (!"Bundle".equals(resourceType)) {
                            throw new IllegalArgumentException(
                                    "Input must be a FHIR Bundle. Received resourceType: " + resourceType);
                        }
                    }
                }
            }
        }

        FhirToHl7Result result = convertFhirToHl7WithResult(fhirJson);

        if (result.getHl7Message() == null) {
            String errorMsg = result.getErrors().isEmpty() ? "Unknown error" : result.getErrors().get(0).getMessage();
            throw new Exception("FHIR to HL7 conversion failed: " + errorMsg);
        }

        return result.getHl7Message();
    }

    /**
     * Detect the appropriate HL7 message type based on bundle content.
     * Prioritizes 'message' type Bundles with explicit MessageHeader.
     */
    private MessageType detectMessageType(Bundle bundle) {
        // 1. Priority: Check for explicit MessageHeader in a 'message' type bundle
        if (bundle.getType() == Bundle.BundleType.MESSAGE && !bundle.getEntry().isEmpty()) {
            Resource firstRes = bundle.getEntryFirstRep().getResource();
            if (firstRes instanceof MessageHeader) {
                MessageHeader header = (MessageHeader) firstRes;
                if (header.hasEventCoding()) {
                    String code = header.getEventCoding().getCode();
                    if (code != null) {
                        code = code.toUpperCase();
                        if (code.contains("ADT"))
                            return MessageType.ADT_A01;
                        if (code.contains("ORM") || code.contains("O01"))
                            return MessageType.ORM_O01;
                        if (code.contains("ORU") || code.contains("R01"))
                            return MessageType.ORU_R01;
                        if (code.contains("SIU") || code.contains("S12"))
                            return MessageType.SIU_S12;
                        if (code.contains("MDM") || code.contains("T02"))
                            return MessageType.MDM_T02;
                    }
                }
            }
        }

        // 2. Fallback: Content-based detection
        boolean hasPatient = false;
        boolean hasEncounter = false;
        boolean hasDiagnosticReport = false;
        boolean hasServiceRequest = false;
        boolean hasAppointment = false;
        boolean hasDocumentReference = false;
        boolean hasMedicationRequest = false;

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.hasResource()) {
                Resource resource = entry.getResource();
                switch (resource.getResourceType()) {
                    case Patient:
                        hasPatient = true;
                        break;
                    case Encounter:
                        hasEncounter = true;
                        break;
                    case DiagnosticReport:
                        hasDiagnosticReport = true;
                        break;
                    case ServiceRequest:
                        hasServiceRequest = true;
                        break;
                    case Appointment:
                        hasAppointment = true;
                        break;
                    case DocumentReference:
                        hasDocumentReference = true;
                        break;
                    case MedicationRequest:
                        hasMedicationRequest = true;
                        break;
                    case CarePlan:
                        hasServiceRequest = true; // CarePlan maps to ORC, similar to ServiceRequest/ORM
                        break;
                    default:
                        break;
                }
            }
        }

        // Priority-based detection
        if (hasDiagnosticReport) {
            return MessageType.ORU_R01; // Lab/Diagnostic Results
        } else if (hasDocumentReference) {
            return MessageType.MDM_T02; // Document notification
        } else if (hasAppointment) {
            return MessageType.SIU_S12; // Scheduling
        } else if (hasServiceRequest || hasMedicationRequest) {
            return MessageType.ORM_O01; // General Order
        } else if (hasPatient || hasEncounter) {
            return MessageType.ADT_A01; // ADT (default for patient/encounter data)
        }

        return MessageType.ADT_A01; // Default fallback
    }

    /**
     * Create the appropriate HL7 message structure.
     */
    private Message createHl7Message(MessageType messageType) throws Exception {
        switch (messageType) {
            case ORU_R01:
                ORU_R01 oru = new ORU_R01();
                oru.initQuickstart("ORU", "R01", "P");
                return oru;
            case ORM_O01:
                ORM_O01 orm = new ORM_O01();
                orm.initQuickstart("ORM", "O01", "P");
                return orm;
            case SIU_S12:
                SIU_S12 siu = new SIU_S12();
                siu.initQuickstart("SIU", "S12", "P");
                return siu;
            case MDM_T02:
                MDM_T02 mdm = new MDM_T02();
                mdm.initQuickstart("MDM", "T02", "P");
                return mdm;
            case ADT_A01:
            default:
                ADT_A01 adt = new ADT_A01();
                adt.initQuickstart("ADT", "A01", "P");
                return adt;
        }
    }

    /**
     * Populate the MSH segment.
     */
    private void populateMsh(Message message, MessageType messageType, Bundle bundle) throws Exception {
        Terser terser = new Terser(message);

        terser.set("MSH-3", "fhirhl7transformer");
        terser.set("MSH-5", "LegacyApp");
        terser.set("MSH-7", new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

        // Use Bundle ID as MSH-10 (Preserve Transaction ID)
        if (bundle.hasId()) {
            terser.set("MSH-10", bundle.getIdElement().getIdPart());
        } else {
            terser.set("MSH-10", java.util.UUID.randomUUID().toString());
        }
    }

    /**
     * Supported HL7 message types.
     */
    public enum MessageType {
        ADT_A01, // Admit/Visit Notification
        ORM_O01, // General Order Message
        ORU_R01, // Unsolicited Observation Message
        SIU_S12, // Scheduling Information Unsolicited
        MDM_T02 // Document Status Notification
    }

    /**
     * Result wrapper for FHIR to HL7 conversion.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FhirToHl7Result {
        private String hl7Message;
        private String messageType;
        @lombok.Builder.Default
        private List<ConversionError> errors = new ArrayList<>();
        @lombok.Builder.Default
        private List<ConversionError> warnings = new ArrayList<>();
        private int successCount;
        private int failCount;

        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }

        public boolean isPartialSuccess() {
            return hl7Message != null && hasErrors();
        }

        public boolean isFullSuccess() {
            return hl7Message != null && !hasErrors();
        }
    }
}
