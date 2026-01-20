package com.al.fhirhl7transformer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for FHIR to HL7 mapping.
 * Loaded from fhir-mapping-config.yml
 */
@Configuration
@ConfigurationProperties(prefix = "fhir-to-hl7")
@Data
public class FhirMappingConfig {

    /**
     * Target HL7 version (2.3, 2.4, 2.5)
     */
    private String hl7Version = "2.5";

    /**
     * Default message type when not auto-detected
     */
    private String defaultMessageType = "ADT^A01";

    /**
     * Sending application name
     */
    private String sendingApplication = "fhirhl7transformer";

    /**
     * Receiving application name
     */
    private String receivingApplication = "LegacyApp";

    /**
     * Sending facility name
     */
    private String sendingFacility = "FHIR_FACILITY";

    /**
     * Receiving facility name
     */
    private String receivingFacility = "HL7_FACILITY";

    /**
     * Converter enable/disable flags
     */
    private Map<String, Boolean> converters = new HashMap<>();

    /**
     * Patient field mappings
     */
    private PatientMappings patient = new PatientMappings();

    /**
     * Encounter field mappings
     */
    private EncounterMappings encounter = new EncounterMappings();

    /**
     * Observation field mappings
     */
    private ObservationMappings observation = new ObservationMappings();

    /**
     * Z-segment configuration
     */
    private ZSegmentConfig zSegments = new ZSegmentConfig();

    /**
     * Message type detection rules
     */
    private MessageTypeDetection messageTypeDetection = new MessageTypeDetection();

    /**
     * Check if a specific converter is enabled.
     */
    public boolean isConverterEnabled(String converterName) {
        return converters.getOrDefault(converterName, true);
    }

    @Data
    public static class PatientMappings {
        private Map<String, String> identifierTypeCodeMap = new HashMap<>();
        private Map<String, String> genderMap = new HashMap<>();
        private Map<String, String> maritalStatusMap = new HashMap<>();
    }

    @Data
    public static class EncounterMappings {
        private Map<String, String> classMap = new HashMap<>();
        private Map<String, String> statusMap = new HashMap<>();
    }

    @Data
    public static class ObservationMappings {
        private Map<String, String> valueTypeMap = new HashMap<>();
        private Map<String, String> statusMap = new HashMap<>();
    }

    @Data
    public static class ZSegmentConfig {
        private boolean enabled = true;
        private Map<String, ZSegmentDefinition> customSegments = new HashMap<>();
    }

    @Data
    public static class ZSegmentDefinition {
        private String description;
        private Map<Integer, String> fields = new HashMap<>();
    }

    @Data
    public static class MessageTypeDetection {
        private List<MessageTypeRule> rules;
    }

    @Data
    public static class MessageTypeRule {
        private List<String> resources;
        private String messageType;
    }
}
