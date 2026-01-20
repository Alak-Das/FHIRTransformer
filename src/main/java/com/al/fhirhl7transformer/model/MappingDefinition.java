package com.al.fhirhl7transformer.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MappingDefinition {
    private String id;
    private String description;
    private String sourceSegment; // e.g. "PID"
    private String targetResource; // e.g. "Patient"
    private List<FieldMapping> mappings;

    @Data
    public static class FieldMapping {
        private String sourceField; // e.g. "PID-5-1"
        private String targetField; // e.g. "name[0].family"
        private String transformation; // e.g. "UPPERCASE", "DATE_FORMAT"
        private Map<String, String> params;
    }
}
