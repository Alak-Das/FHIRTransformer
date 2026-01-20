package com.al.fhirhl7transformer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "fhir-to-hl7")
public class MappingConfiguration {

    private MessageTypeDetection messageTypeDetection;
    private PatientMapping patient;

    @Data
    public static class MessageTypeDetection {
        private List<MessageTypeRule> rules;
    }

    @Data
    public static class MessageTypeRule {
        private List<String> resources;
        private String messageType;
    }

    @Data
    public static class PatientMapping {
        private Map<String, String> genderMap;
        private Map<String, String> maritalStatusMap;
        private Map<String, String> identifierTypeCodeMap;
    }
}
