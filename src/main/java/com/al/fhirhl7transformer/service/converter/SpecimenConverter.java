package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converter for creating FHIR Specimen resources from HL7 SPM segments.
 * 
 * SPM Segment Structure:
 * - SPM-1: Set ID
 * - SPM-2: Specimen ID (EIP)
 * - SPM-3: Specimen Parent IDs
 * - SPM-4: Specimen Type (CWE)
 * - SPM-5: Specimen Type Modifier
 * - SPM-6: Specimen Additives
 * - SPM-7: Specimen Collection Method
 * - SPM-8: Specimen Source Site
 * - SPM-9: Specimen Source Site Modifier
 * - SPM-10: Specimen Collection Site
 * - SPM-11: Specimen Role
 * - SPM-12: Specimen Collection Amount
 * - SPM-17: Specimen Collection Date/Time
 * - SPM-18: Specimen Received Date/Time
 * - SPM-19: Specimen Expiration Date/Time
 * - SPM-20: Specimen Availability
 * - SPM-21: Specimen Reject Reason
 * - SPM-24: Specimen Condition
 */
@Component
@Slf4j
public class SpecimenConverter implements SegmentConverter<Specimen> {

    @Override
    public List<Specimen> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<Specimen> specimens = new ArrayList<>();

        int spmIndex = 0;
        while (spmIndex < 50) { // Safety limit
            try {
                String spmPath = "/.SPM(" + spmIndex + ")";
                boolean found = false;

                try {
                    if (terser.getSegment(spmPath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // Try SPECIMEN group path
                    String specPath = "/.SPECIMEN(" + spmIndex + ")/SPM";
                    try {
                        if (terser.getSegment(specPath) != null) {
                            spmPath = specPath;
                            found = true;
                        }
                    } catch (Exception ex) {
                        // Not found
                    }
                }

                if (!found)
                    break;

                // Check if we have a specimen ID
                String specimenId = terser.get(spmPath + "-2-1");
                String specimenType = terser.get(spmPath + "-4-1");

                if (isEmpty(specimenId) && isEmpty(specimenType)) {
                    spmIndex++;
                    continue;
                }

                log.info("Processing Specimen from SPM({}): ID={}, Type={}", spmIndex, specimenId, specimenType);

                Specimen specimen = new Specimen();
                specimen.setId(UUID.randomUUID().toString());
                specimen.setStatus(Specimen.SpecimenStatus.AVAILABLE);

                // SPM-2: Specimen ID
                if (!isEmpty(specimenId)) {
                    specimen.addIdentifier()
                            .setSystem("urn:oid:specimen-id")
                            .setValue(specimenId);

                    // Also set accession identifier
                    String accessionId = terser.get(spmPath + "-2-2");
                    if (!isEmpty(accessionId)) {
                        specimen.setAccessionIdentifier(new Identifier()
                                .setSystem("urn:oid:accession-id")
                                .setValue(accessionId));
                    }
                }

                // SPM-4: Specimen Type
                if (!isEmpty(specimenType)) {
                    String typeDisplay = terser.get(spmPath + "-4-2");
                    String typeSystem = terser.get(spmPath + "-4-3");

                    CodeableConcept type = new CodeableConcept();
                    Coding coding = type.addCoding()
                            .setCode(specimenType)
                            .setDisplay(typeDisplay);

                    if (!isEmpty(typeSystem)) {
                        coding.setSystem(mapCodeSystem(typeSystem));
                    }

                    specimen.setType(type);
                }

                // SPM-7: Collection Method
                String collectionMethod = terser.get(spmPath + "-7-1");
                if (!isEmpty(collectionMethod)) {
                    String methodDisplay = terser.get(spmPath + "-7-2");
                    Specimen.SpecimenCollectionComponent collection = specimen.getCollection();
                    collection.setMethod(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setCode(collectionMethod)
                                    .setDisplay(methodDisplay)));
                }

                // SPM-8: Specimen Source Site (Body Site)
                String sourceSite = terser.get(spmPath + "-8-1");
                if (!isEmpty(sourceSite)) {
                    String siteDisplay = terser.get(spmPath + "-8-2");
                    Specimen.SpecimenCollectionComponent collection = specimen.getCollection();
                    collection.setBodySite(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setCode(sourceSite)
                                    .setDisplay(siteDisplay)));
                }

                // SPM-12: Collection Amount/Quantity
                String quantity = terser.get(spmPath + "-12-1");
                if (!isEmpty(quantity)) {
                    try {
                        String unit = terser.get(spmPath + "-12-2");
                        Specimen.SpecimenCollectionComponent collection = specimen.getCollection();
                        collection.setQuantity(new Quantity()
                                .setValue(Double.parseDouble(quantity))
                                .setUnit(unit));
                    } catch (NumberFormatException e) {
                        log.debug("Could not parse specimen quantity: {}", quantity);
                    }
                }

                // SPM-17: Collection Date/Time
                String collectionDateTime = terser.get(spmPath + "-17-1");
                if (!isEmpty(collectionDateTime)) {
                    try {
                        Specimen.SpecimenCollectionComponent collection = specimen.getCollection();
                        collection.setCollected(new DateTimeType(parseHl7DateTime(collectionDateTime)));
                    } catch (Exception e) {
                        log.debug("Could not parse collection date: {}", collectionDateTime);
                    }
                }

                // SPM-18: Received Date/Time
                String receivedDateTime = terser.get(spmPath + "-18-1");
                if (!isEmpty(receivedDateTime)) {
                    try {
                        specimen.setReceivedTimeElement(new DateTimeType(parseHl7DateTime(receivedDateTime)));
                    } catch (Exception e) {
                        log.debug("Could not parse received date: {}", receivedDateTime);
                    }
                }

                // SPM-20: Specimen Availability
                String availability = terser.get(spmPath + "-20");
                if (!isEmpty(availability)) {
                    specimen.setStatus(mapSpecimenStatus(availability));
                }

                // SPM-21: Reject Reason
                String rejectReason = terser.get(spmPath + "-21-1");
                if (!isEmpty(rejectReason)) {
                    specimen.setStatus(Specimen.SpecimenStatus.UNSATISFACTORY);
                    String rejectDisplay = terser.get(spmPath + "-21-2");
                    specimen.addCondition(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setCode(rejectReason)
                                    .setDisplay(rejectDisplay)));
                }

                // SPM-24: Specimen Condition
                String condition = terser.get(spmPath + "-24-1");
                if (!isEmpty(condition)) {
                    String condDisplay = terser.get(spmPath + "-24-2");
                    specimen.addCondition(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setCode(condition)
                                    .setDisplay(condDisplay)));
                }

                // Link to patient
                if (context != null && context.getPatientId() != null) {
                    specimen.setSubject(new Reference("Patient/" + context.getPatientId()));
                }

                specimens.add(specimen);
                spmIndex++;

            } catch (Exception e) {
                log.error("Error processing SPM segment at index {}", spmIndex, e);
                break;
            }
        }

        log.info("Created {} Specimen resources from SPM segments", specimens.size());
        return specimens;
    }

    private Specimen.SpecimenStatus mapSpecimenStatus(String hl7Status) {
        if (hl7Status == null)
            return Specimen.SpecimenStatus.AVAILABLE;
        switch (hl7Status.toUpperCase()) {
            case "Y":
            case "A":
            case "AVAILABLE":
                return Specimen.SpecimenStatus.AVAILABLE;
            case "N":
            case "U":
            case "UNAVAILABLE":
                return Specimen.SpecimenStatus.UNAVAILABLE;
            case "E":
            case "ENTERED-IN-ERROR":
                return Specimen.SpecimenStatus.ENTEREDINERROR;
            default:
                return Specimen.SpecimenStatus.AVAILABLE;
        }
    }

    private String mapCodeSystem(String hl7System) {
        if (hl7System == null)
            return null;
        switch (hl7System.toUpperCase()) {
            case "SCT":
            case "SNOMED":
                return "http://snomed.info/sct";
            case "LN":
            case "LOINC":
                return "http://loinc.org";
            case "HL70070":
                return "http://terminology.hl7.org/CodeSystem/v2-0070";
            default:
                return "urn:oid:" + hl7System;
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

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
