package com.al.fhirhl7transformer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for HL7 version-specific settings.
 */
@Configuration
@ConfigurationProperties(prefix = "hl7-version-config")
@Data
public class Hl7VersionConfig {

    /**
     * Version-specific configurations keyed by HL7 version (e.g., "2.3", "2.4",
     * "2.5")
     */
    private Map<String, VersionSettings> versions = new HashMap<>();

    /**
     * Get settings for a specific HL7 version.
     */
    public VersionSettings getVersion(String version) {
        return versions.getOrDefault(version, getDefaultSettings());
    }

    /**
     * Get default settings (v2.5)
     */
    public VersionSettings getDefaultSettings() {
        VersionSettings defaults = new VersionSettings();
        defaults.setEncodingCharacters("^~\\&");
        defaults.setDateFormat("yyyyMMdd");
        defaults.setDatetimeFormat("yyyyMMddHHmmss");
        defaults.setSegmentTerminator("\r");
        defaults.setUseEscapeSequences(true);
        return defaults;
    }

    @Data
    public static class VersionSettings {
        /**
         * HL7 encoding characters (typically ^~\&)
         */
        private String encodingCharacters = "^~\\&";

        /**
         * Date format for TS fields
         */
        private String dateFormat = "yyyyMMdd";

        /**
         * DateTime format for TS fields
         */
        private String datetimeFormat = "yyyyMMddHHmmss";

        /**
         * Segment terminator character
         */
        private String segmentTerminator = "\r";

        /**
         * Whether to use escape sequences for special characters
         */
        private boolean useEscapeSequences = true;

        /**
         * Maximum field length (0 = unlimited)
         */
        private int maxFieldLength = 0;

        /**
         * Maximum segment repetitions
         */
        private int maxSegmentRepetitions = 999;
    }
}
