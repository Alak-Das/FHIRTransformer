package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converter for creating FHIR DocumentReference resources from HL7 TXA segment.
 * Used primarily for MDM^T02 (Document Notification) messages.
 * 
 * TXA Segment Structure:
 * - TXA-1: Set ID
 * - TXA-2: Document Type
 * - TXA-3: Document Content Presentation
 * - TXA-4: Activity Date/Time
 * - TXA-5: Primary Activity Provider
 * - TXA-6: Origination Date/Time
 * - TXA-7: Transcription Date/Time
 * - TXA-8: Edit Date/Time
 * - TXA-9: Originator Code/Name
 * - TXA-10: Assigned Document Authenticator
 * - TXA-11: Transcriptionist Code/Name
 * - TXA-12: Unique Document Number (Master ID)
 * - TXA-13: Parent Document Number
 * - TXA-14: Placer Order Number
 * - TXA-15: Filler Order Number
 * - TXA-16: Unique Document File Name
 * - TXA-17: Document Completion Status
 * - TXA-18: Document Confidentiality Status
 * - TXA-19: Document Availability Status
 * - TXA-20: Document Storage Status
 * - TXA-21: Document Change Reason
 * - TXA-22: Authentication Person, Time Stamp
 * - TXA-23: Distributed Copies
 */
@Component
@Slf4j
public class DocumentReferenceConverter implements SegmentConverter<DocumentReference> {

    @Override
    public List<DocumentReference> convert(Terser terser, Bundle bundle, ConversionContext context) {
        List<DocumentReference> documents = new ArrayList<>();

        int txaIndex = 0;
        while (txaIndex < 20) { // Safety limit
            try {
                String txaPath = "/.TXA(" + txaIndex + ")";
                boolean found = false;

                try {
                    if (terser.getSegment(txaPath) != null) {
                        found = true;
                    }
                } catch (Exception e) {
                    // TXA might be nested
                }

                if (!found)
                    break;

                // TXA-12: Unique Document Number (required)
                String uniqueDocNumber = terser.get(txaPath + "-12-1");
                String documentType = terser.get(txaPath + "-2-1");

                if (isEmpty(uniqueDocNumber) && isEmpty(documentType)) {
                    txaIndex++;
                    continue;
                }

                log.info("Processing DocumentReference from TXA({}): DocNum={}, Type={}",
                        txaIndex, uniqueDocNumber, documentType);

                DocumentReference docRef = new DocumentReference();
                docRef.setId(UUID.randomUUID().toString());

                // TXA-12: Master Identifier
                if (!isEmpty(uniqueDocNumber)) {
                    docRef.setMasterIdentifier(new Identifier()
                            .setSystem("urn:oid:document-id")
                            .setValue(uniqueDocNumber));
                }

                // TXA-2: Document Type
                if (!isEmpty(documentType)) {
                    String typeDisplay = terser.get(txaPath + "-2-2");
                    String typeSystem = terser.get(txaPath + "-2-3");

                    docRef.setType(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setSystem(mapCodeSystem(typeSystem))
                                    .setCode(documentType)
                                    .setDisplay(typeDisplay)));
                }

                // TXA-3: Document Content Presentation (format)
                String contentPresentation = terser.get(txaPath + "-3");
                if (!isEmpty(contentPresentation)) {
                    DocumentReference.DocumentReferenceContentComponent content = docRef.addContent();
                    content.setFormat(new Coding()
                            .setSystem("http://ihe.net/fhir/ValueSet/IHE.FormatCode.codesystem")
                            .setCode(contentPresentation));

                    // Add placeholder attachment (actual content would come from OBX)
                    content.setAttachment(new Attachment()
                            .setContentType(mapContentType(contentPresentation)));
                }

                // TXA-4: Activity Date/Time
                String activityDateTime = terser.get(txaPath + "-4-1");
                if (!isEmpty(activityDateTime)) {
                    try {
                        docRef.setDateElement(new InstantType(parseHl7DateTime(activityDateTime)));
                    } catch (Exception e) {
                        log.debug("Could not parse activity date: {}", activityDateTime);
                    }
                }

                // TXA-5: Primary Activity Provider -> author
                String activityProvider = terser.get(txaPath + "-5-1");
                if (!isEmpty(activityProvider)) {
                    String providerName = terser.get(txaPath + "-5-2");
                    docRef.addAuthor(new Reference().setDisplay(
                            !isEmpty(providerName) ? providerName : activityProvider));
                }

                // TXA-9: Originator -> author
                String originatorId = terser.get(txaPath + "-9-1");
                String originatorName = terser.get(txaPath + "-9-2");
                if (!isEmpty(originatorId) || !isEmpty(originatorName)) {
                    String display = !isEmpty(originatorName) ? originatorName : originatorId;
                    // Check if not already added
                    if (docRef.getAuthor().stream()
                            .noneMatch(a -> display.equals(a.getDisplay()))) {
                        docRef.addAuthor(new Reference().setDisplay(display));
                    }
                }

                // TXA-16: Document File Name
                String fileName = terser.get(txaPath + "-16");
                if (!isEmpty(fileName)) {
                    if (docRef.getContent().isEmpty()) {
                        docRef.addContent().setAttachment(new Attachment()
                                .setTitle(fileName));
                    } else {
                        docRef.getContentFirstRep().getAttachment().setTitle(fileName);
                    }
                }

                // TXA-17: Document Completion Status -> status
                String completionStatus = terser.get(txaPath + "-17");
                docRef.setStatus(mapDocumentStatus(completionStatus));

                // TXA-18: Document Confidentiality Status -> securityLabel
                String confidentiality = terser.get(txaPath + "-18");
                if (!isEmpty(confidentiality)) {
                    docRef.addSecurityLabel(new CodeableConcept()
                            .addCoding(new Coding()
                                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-Confidentiality")
                                    .setCode(mapConfidentiality(confidentiality))));
                }

                // TXA-22: Authentication Person, Time Stamp -> authenticator
                String authenticatorId = terser.get(txaPath + "-22-1");
                String authenticatorName = terser.get(txaPath + "-22-2");
                if (!isEmpty(authenticatorId) || !isEmpty(authenticatorName)) {
                    String display = !isEmpty(authenticatorName) ? authenticatorName : authenticatorId;
                    docRef.setAuthenticator(new Reference().setDisplay(display));
                }

                // TXA-13: Parent Document Number -> relatesTo
                String parentDocNumber = terser.get(txaPath + "-13-1");
                if (!isEmpty(parentDocNumber)) {
                    DocumentReference.DocumentReferenceRelatesToComponent relatesTo = docRef.addRelatesTo();
                    relatesTo.setCode(DocumentReference.DocumentRelationshipType.REPLACES);
                    relatesTo.setTarget(new Reference()
                            .setIdentifier(new Identifier()
                                    .setSystem("urn:oid:document-id")
                                    .setValue(parentDocNumber)));
                }

                // Link to patient
                if (context != null && context.getPatientId() != null) {
                    docRef.setSubject(new Reference("Patient/" + context.getPatientId()));
                }

                // Link to encounter
                if (context != null && context.getEncounterId() != null) {
                    DocumentReference.DocumentReferenceContextComponent docContext = new DocumentReference.DocumentReferenceContextComponent();
                    docContext.addEncounter(new Reference("Encounter/" + context.getEncounterId()));
                    docRef.setContext(docContext);
                }

                documents.add(docRef);
                txaIndex++;

            } catch (Exception e) {
                log.error("Error processing TXA segment at index {}", txaIndex, e);
                break;
            }
        }

        log.info("Created {} DocumentReference resources from TXA segments", documents.size());
        return documents;
    }

    /**
     * Map TXA-17 Document Completion Status to FHIR DocumentReference status
     */
    private Enumerations.DocumentReferenceStatus mapDocumentStatus(String hl7Status) {
        if (hl7Status == null)
            return Enumerations.DocumentReferenceStatus.CURRENT;
        switch (hl7Status.toUpperCase()) {
            case "AU": // Authenticated
            case "LA": // Legally authenticated
                return Enumerations.DocumentReferenceStatus.CURRENT;
            case "IN": // Incomplete
            case "IP": // In progress
            case "PA": // Pre-authenticated
                return Enumerations.DocumentReferenceStatus.CURRENT; // Still current, just not final
            case "DI": // Dictated
                return Enumerations.DocumentReferenceStatus.CURRENT;
            case "DO": // Document
            case "CA": // Cancel (availability)
                return Enumerations.DocumentReferenceStatus.ENTEREDINERROR;
            default:
                return Enumerations.DocumentReferenceStatus.CURRENT;
        }
    }

    /**
     * Map TXA-18 Confidentiality to FHIR Confidentiality codes
     */
    private String mapConfidentiality(String hl7Confidentiality) {
        if (hl7Confidentiality == null)
            return "N"; // Normal
        switch (hl7Confidentiality.toUpperCase()) {
            case "V":
                return "V"; // Very restricted
            case "R":
                return "R"; // Restricted
            case "U":
                return "U"; // Unrestricted
            case "N":
                return "N"; // Normal
            default:
                return "N";
        }
    }

    /**
     * Map TXA-3 content presentation to MIME type
     */
    private String mapContentType(String presentation) {
        if (presentation == null)
            return "text/plain";
        switch (presentation.toUpperCase()) {
            case "TX":
                return "text/plain";
            case "FT":
                return "text/plain";
            case "RTF":
                return "application/rtf";
            case "HTML":
                return "text/html";
            case "PDF":
                return "application/pdf";
            case "CDA":
                return "application/xml";
            case "CCDA":
                return "application/xml+cda";
            default:
                return "text/plain";
        }
    }

    private String mapCodeSystem(String hl7System) {
        if (hl7System == null)
            return null;
        switch (hl7System.toUpperCase()) {
            case "LN":
            case "LOINC":
                return "http://loinc.org";
            case "DCM":
                return "http://dicom.nema.org/resources/ontology/DCM";
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
            sb.append("Z"); // Assume UTC for Instant
        }

        return sb.toString();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
