package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converter for creating FHIR Communication resources from HL7 NTE (Notes and
 * Comments) segments.
 * 
 * NTE Segment Structure:
 * - NTE-1: Set ID
 * - NTE-2: Source of Comment (L=Lab, P=Pathologist, etc.)
 * - NTE-3: Comment (text, repeatable)
 * - NTE-4: Comment Type (CWE)
 * - NTE-5: Entered By
 * - NTE-6: Entered Date/Time
 */
@Component
@Slf4j
public class CommunicationConverter implements SegmentConverter<Communication> {

    @Override
    public List<Communication> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Communication> communications = new ArrayList<>();

        int nteIndex = 0;
        while (nteIndex < 100) { // Safety limit (notes can be numerous)
            try {
                String ntePath = "/.NTE(" + nteIndex + ")";
                boolean found = false;

                try {
                    if (terser.getSegment(ntePath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // NTE might be nested in various groups
                    // Try alternative paths
                }

                if (!found)
                    break;

                // NTE-3: Comment text (required for meaningful note)
                String comment = terser.get(ntePath + "-3");

                if (isEmpty(comment)) {
                    nteIndex++;
                    continue;
                }

                log.debug("Processing NTE({}): {}", nteIndex, truncate(comment, 50));

                Communication comm = new Communication();
                comm.setId(UUID.randomUUID().toString());
                comm.setStatus(Communication.CommunicationStatus.COMPLETED);

                // Set payload with the comment text
                Communication.CommunicationPayloadComponent payload = comm.addPayload();
                payload.setContent(new StringType(comment));

                // NTE-2: Source of Comment
                String source = terser.get(ntePath + "-2");
                if (!isEmpty(source)) {
                    comm.addCategory(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setSystem("http://terminology.hl7.org/CodeSystem/v2-0105")
                                    .setCode(source)
                                    .setDisplay(mapSourceDisplay(source))));
                }

                // NTE-4: Comment Type
                String commentType = terser.get(ntePath + "-4-1");
                if (!isEmpty(commentType)) {
                    String typeDisplay = terser.get(ntePath + "-4-2");
                    comm.addCategory(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setCode(commentType)
                                    .setDisplay(typeDisplay)));
                }

                // NTE-5: Entered By
                String enteredBy = terser.get(ntePath + "-5-1");
                if (!isEmpty(enteredBy)) {
                    String enteredByName = terser.get(ntePath + "-5-2");
                    comm.setSender(new Reference()
                            .setDisplay(isEmpty(enteredByName) ? enteredBy : enteredByName + " (" + enteredBy + ")"));
                }

                // NTE-6: Entered Date/Time
                String enteredDateTime = terser.get(ntePath + "-6");
                if (!isEmpty(enteredDateTime)) {
                    try {
                        comm.setSentElement(new DateTimeType(parseHl7DateTime(enteredDateTime)));
                    } catch (Exception e) {
                        log.debug("Could not parse entered date: {}", enteredDateTime);
                    }
                }

                // Link to patient as subject
                if (context != null && context.getPatientId() != null) {
                    comm.setSubject(new Reference("Patient/" + context.getPatientId()));
                }

                // Link to encounter if available
                if (context != null && context.getEncounterId() != null) {
                    comm.setEncounter(new Reference("Encounter/" + context.getEncounterId()));
                }

                communications.add(comm);
                nteIndex++;

            } catch (Exception e) {
                log.error("Error processing NTE segment at index {}", nteIndex, e);
                break;
            }
        }

        log.info("Created {} Communication resources from NTE segments", communications.size());
        return communications;
    }

    /**
     * Map NTE-2 source codes to display text
     */
    private String mapSourceDisplay(String source) {
        if (source == null)
            return null;
        switch (source.toUpperCase()) {
            case "L":
                return "Lab";
            case "P":
                return "Pathologist";
            case "C":
                return "Clinician";
            case "R":
                return "Radiologist";
            case "A":
                return "Ancillary";
            case "F":
                return "Filler";
            case "O":
                return "Other";
            default:
                return source;
        }
    }

    private String parseHl7DateTime(String hl7DateTime) {
        if (hl7DateTime == null || hl7DateTime.length() < 8)
            return null;

        StringBuilder sb = new StringBuilder();
        sb.append(hl7DateTime.substring(0, 4)).append("-");
        sb.append(hl7DateTime.substring(4, 6)).append("-");
        sb.append(hl7DateTime.substring(6, 8));

        if (hl7DateTime.length() >= 12) {
            sb.append("T");
            sb.append(hl7DateTime.substring(8, 10)).append(":");
            sb.append(hl7DateTime.substring(10, 12));
            if (hl7DateTime.length() >= 14) {
                sb.append(":").append(hl7DateTime.substring(12, 14));
            }
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return null;
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
