package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts FHIR Extensions to HL7 Z-segments (custom segments).
 * This converter handles any FHIR resource that has extensions with Z-segment
 * URLs.
 * 
 * Extension URL patterns:
 * - urn:hl7:zsegment:ZXX-N for field N of segment ZXX
 * - http://example.org/fhir/zsegment/ZXX/N for field N of segment ZXX
 */
@Component
public class ExtensionToZSegmentConverter implements FhirToHl7Converter<DomainResource> {

    // Track Z-segment indices for repeating segments
    private final Map<String, Integer> zSegmentIndices = new HashMap<>();

    @Override
    public boolean canConvert(Resource resource) {
        // This converter handles any DomainResource that has Z-segment extensions
        if (resource instanceof DomainResource) {
            DomainResource dr = (DomainResource) resource;
            if (dr.hasExtension()) {
                for (Extension ext : dr.getExtension()) {
                    if (isZSegmentExtension(ext.getUrl())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void convert(DomainResource resource, Message message, Terser terser) throws HL7Exception {
        if (!resource.hasExtension()) {
            return;
        }

        for (Extension ext : resource.getExtension()) {
            String url = ext.getUrl();
            if (!isZSegmentExtension(url)) {
                continue;
            }

            ZSegmentInfo info = parseZSegmentUrl(url);
            if (info == null) {
                continue;
            }

            String segmentPath = buildSegmentPath(info);

            // Convert extension value to HL7 field value
            String value = convertExtensionValue(ext);
            if (value != null && !value.isEmpty()) {
                terser.set(segmentPath, value);
            }

            // Handle nested extensions (for subfields)
            if (ext.hasExtension()) {
                for (Extension subExt : ext.getExtension()) {
                    String subUrl = subExt.getUrl();
                    // Subextension URL should be just the subfield number
                    try {
                        int subfield = Integer.parseInt(subUrl);
                        String subValue = convertExtensionValue(subExt);
                        if (subValue != null && !subValue.isEmpty()) {
                            terser.set(segmentPath + "-" + subfield, subValue);
                        }
                    } catch (NumberFormatException e) {
                        // Subextension URL is not a number, might be a named component
                        ZSegmentInfo subInfo = parseZSegmentUrl(subUrl);
                        if (subInfo != null) {
                            String subPath = buildSegmentPath(subInfo);
                            String subValue = convertExtensionValue(subExt);
                            if (subValue != null && !subValue.isEmpty()) {
                                terser.set(subPath, subValue);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if the extension URL indicates a Z-segment.
     */
    private boolean isZSegmentExtension(String url) {
        if (url == null)
            return false;
        return url.toLowerCase().contains("zsegment")
                || url.matches(".*[/:]Z[A-Z0-9]{2}[-/].*")
                || url.startsWith("urn:hl7:z");
    }

    /**
     * Parse Z-segment information from extension URL.
     */
    private ZSegmentInfo parseZSegmentUrl(String url) {
        // Pattern: urn:hl7:zsegment:ZXX-N or urn:hl7:zsegment:ZXX-N-M
        if (url.contains("zsegment:")) {
            String[] parts = url.split("zsegment:")[1].split("-");
            if (parts.length >= 2) {
                ZSegmentInfo info = new ZSegmentInfo();
                info.segmentName = parts[0].toUpperCase();
                try {
                    info.fieldNumber = Integer.parseInt(parts[1]);
                    if (parts.length >= 3) {
                        info.componentNumber = Integer.parseInt(parts[2]);
                    }
                    return info;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        // Pattern: .../ZXX/N or .../ZXX/N/M
        String[] urlParts = url.split("/");
        for (int i = 0; i < urlParts.length - 1; i++) {
            String part = urlParts[i];
            if (part.matches("Z[A-Z0-9]{2}")) {
                ZSegmentInfo info = new ZSegmentInfo();
                info.segmentName = part;
                try {
                    info.fieldNumber = Integer.parseInt(urlParts[i + 1]);
                    if (i + 2 < urlParts.length) {
                        info.componentNumber = Integer.parseInt(urlParts[i + 2]);
                    }
                    return info;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Build Terser path for Z-segment.
     */
    private String buildSegmentPath(ZSegmentInfo info) {
        // Get or create index for repeating segments
        int index = zSegmentIndices.getOrDefault(info.segmentName, 0);

        StringBuilder path = new StringBuilder();
        path.append("/.").append(info.segmentName);
        if (index > 0) {
            path.append("(").append(index).append(")");
        }
        path.append("-").append(info.fieldNumber);

        if (info.componentNumber > 0) {
            path.append("-").append(info.componentNumber);
        }

        return path.toString();
    }

    /**
     * Convert FHIR extension value to string for HL7.
     */
    private String convertExtensionValue(Extension ext) {
        if (!ext.hasValue()) {
            return null;
        }

        Type value = ext.getValue();

        if (value instanceof StringType) {
            return ((StringType) value).getValue();
        } else if (value instanceof IntegerType) {
            return String.valueOf(((IntegerType) value).getValue());
        } else if (value instanceof DecimalType) {
            return ((DecimalType) value).getValueAsString();
        } else if (value instanceof BooleanType) {
            return ((BooleanType) value).getValue() ? "Y" : "N";
        } else if (value instanceof DateType) {
            return new java.text.SimpleDateFormat("yyyyMMdd")
                    .format(((DateType) value).getValue());
        } else if (value instanceof DateTimeType) {
            return new java.text.SimpleDateFormat("yyyyMMddHHmmss")
                    .format(((DateTimeType) value).getValue());
        } else if (value instanceof CodeType) {
            return ((CodeType) value).getValue();
        } else if (value instanceof Coding) {
            Coding coding = (Coding) value;
            return coding.hasCode() ? coding.getCode() : coding.getDisplay();
        } else if (value instanceof CodeableConcept) {
            CodeableConcept cc = (CodeableConcept) value;
            if (cc.hasCoding()) {
                return cc.getCodingFirstRep().getCode();
            }
            return cc.getText();
        } else if (value instanceof Identifier) {
            return ((Identifier) value).getValue();
        } else if (value instanceof Reference) {
            Reference ref = (Reference) value;
            if (ref.hasReference()) {
                String refStr = ref.getReference();
                return refStr.contains("/") ? refStr.substring(refStr.lastIndexOf("/") + 1) : refStr;
            }
            return ref.getDisplay();
        }

        // Fallback: try toString
        return value.toString();
    }

    /**
     * Increment segment index for repeating Z-segments.
     */
    public void incrementSegmentIndex(String segmentName) {
        zSegmentIndices.merge(segmentName, 1, (a, b) -> a + b);
    }

    /**
     * Reset all segment indices.
     */
    public void resetIndices() {
        zSegmentIndices.clear();
    }

    /**
     * Internal class to hold Z-segment information.
     */
    private static class ZSegmentInfo {
        String segmentName;
        int fieldNumber;
        int componentNumber = 0;
    }
}
