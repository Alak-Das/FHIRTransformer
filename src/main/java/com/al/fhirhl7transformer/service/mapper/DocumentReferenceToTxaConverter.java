package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

/**
 * Converts FHIR DocumentReference to HL7 TXA (Transcription Document Header)
 * segment.
 */
@Component
public class DocumentReferenceToTxaConverter implements FhirToHl7Converter<DocumentReference> {

    private static final SimpleDateFormat HL7_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");
    private int txaIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof DocumentReference;
    }

    @Override
    public void convert(DocumentReference docRef, Message message, Terser terser) throws HL7Exception {
        String txaPath = "/.TXA(" + txaIndex + ")";

        // TXA-1 Set ID
        terser.set(txaPath + "-1", String.valueOf(txaIndex + 1));

        // TXA-2 Document Type
        if (docRef.hasType()) {
            CodeableConcept type = docRef.getType();
            if (type.hasCoding()) {
                Coding coding = type.getCodingFirstRep();
                terser.set(txaPath + "-2", coding.getCode());
            } else if (type.hasText()) {
                terser.set(txaPath + "-2", type.getText());
            }
        }

        // TXA-3 Document Content Presentation
        if (docRef.hasContent()) {
            DocumentReference.DocumentReferenceContentComponent content = docRef.getContentFirstRep();
            if (content.hasAttachment()) {
                Attachment attachment = content.getAttachment();
                if (attachment.hasContentType()) {
                    String contentType = attachment.getContentType();
                    if (contentType.contains("text")) {
                        terser.set(txaPath + "-3", "TX"); // Text
                    } else if (contentType.contains("pdf")) {
                        terser.set(txaPath + "-3", "AP"); // Application
                    } else if (contentType.contains("image")) {
                        terser.set(txaPath + "-3", "IM"); // Image
                    } else {
                        terser.set(txaPath + "-3", "OT"); // Other
                    }
                }
            }
        }

        // TXA-4 Activity Date/Time
        if (docRef.hasDate()) {
            terser.set(txaPath + "-4", HL7_DATE_FORMAT.format(docRef.getDate()));
        }

        // TXA-5 Primary Activity Provider (Author)
        if (docRef.hasAuthor()) {
            Reference author = docRef.getAuthorFirstRep();
            if (author.hasReference()) {
                String ref = author.getReference();
                if (ref.contains("/")) {
                    terser.set(txaPath + "-5-1", ref.substring(ref.lastIndexOf("/") + 1));
                }
            }
            if (author.hasDisplay()) {
                terser.set(txaPath + "-5-2", author.getDisplay());
            }
        }

        // TXA-6 Origination Date/Time
        if (docRef.hasContext() && docRef.getContext().hasPeriod()) {
            Period period = docRef.getContext().getPeriod();
            if (period.hasStart()) {
                terser.set(txaPath + "-6", HL7_DATE_FORMAT.format(period.getStart()));
            }
        }

        // TXA-7 Transcription Date/Time
        // TXA-8 Edit Date/Time

        // TXA-9 Originator Code/Name
        if (docRef.hasCustodian()) {
            Reference custodian = docRef.getCustodian();
            if (custodian.hasDisplay()) {
                terser.set(txaPath + "-9-1", custodian.getDisplay());
            }
        }

        // TXA-11 Originator Code/Name
        // TXA-12 Unique Document Number
        if (docRef.hasIdentifier()) {
            Identifier id = docRef.getIdentifierFirstRep();
            if (id.hasValue()) {
                terser.set(txaPath + "-12-1", id.getValue());
            }
            if (id.hasSystem()) {
                terser.set(txaPath + "-12-2", id.getSystem());
            }
        } else if (docRef.hasId()) {
            terser.set(txaPath + "-12-1", docRef.getIdElement().getIdPart());
        }

        // TXA-13 Parent Document Number
        if (docRef.hasRelatesTo()) {
            for (DocumentReference.DocumentReferenceRelatesToComponent relates : docRef.getRelatesTo()) {
                if (relates.hasTarget()) {
                    Reference target = relates.getTarget();
                    if (target.hasReference()) {
                        String ref = target.getReference();
                        if (ref.contains("/")) {
                            terser.set(txaPath + "-13-1", ref.substring(ref.lastIndexOf("/") + 1));
                        }
                    }
                    break;
                }
            }
        }

        // TXA-14 Placer Order Number
        // TXA-15 Filler Order Number
        if (docRef.hasContext() && docRef.getContext().hasRelated()) {
            for (Reference related : docRef.getContext().getRelated()) {
                if (related.hasReference()) {
                    String ref = related.getReference();
                    if (ref.contains("ServiceRequest") || ref.contains("Order")) {
                        if (ref.contains("/")) {
                            terser.set(txaPath + "-14", ref.substring(ref.lastIndexOf("/") + 1));
                        }
                        break;
                    }
                }
            }
        }

        // TXA-16 Unique Document File Name
        if (docRef.hasContent()) {
            Attachment attachment = docRef.getContentFirstRep().getAttachment();
            if (attachment.hasTitle()) {
                terser.set(txaPath + "-16", attachment.getTitle());
            } else if (attachment.hasUrl()) {
                // Extract filename from URL
                String url = attachment.getUrl();
                if (url.contains("/")) {
                    terser.set(txaPath + "-16", url.substring(url.lastIndexOf("/") + 1));
                }
            }
        }

        // TXA-17 Document Completion Status
        if (docRef.hasStatus()) {
            String status = docRef.getStatus().toCode();
            switch (status) {
                case "current":
                    terser.set(txaPath + "-17", "AU"); // Authenticated
                    break;
                case "superseded":
                    terser.set(txaPath + "-17", "OB"); // Obsolete
                    break;
                case "entered-in-error":
                    terser.set(txaPath + "-17", "CA"); // Cancelled
                    break;
                default:
                    terser.set(txaPath + "-17", "IP"); // In Progress
            }
        }

        // TXA-18 Document Confidentiality Status
        if (docRef.hasSecurityLabel()) {
            CodeableConcept secLabel = docRef.getSecurityLabelFirstRep();
            if (secLabel.hasCoding()) {
                String code = secLabel.getCodingFirstRep().getCode();
                if ("R".equals(code) || "restricted".equalsIgnoreCase(code)) {
                    terser.set(txaPath + "-18", "R");
                } else if ("V".equals(code) || "very-restricted".equalsIgnoreCase(code)) {
                    terser.set(txaPath + "-18", "V");
                } else {
                    terser.set(txaPath + "-18", "U"); // Unrestricted
                }
            }
        }

        // TXA-19 Document Availability Status
        terser.set(txaPath + "-19", "AV"); // Available

        // TXA-21 Document Storage Status
        if (docRef.hasContent() && docRef.getContentFirstRep().hasAttachment()) {
            Attachment attachment = docRef.getContentFirstRep().getAttachment();
            if (attachment.hasData()) {
                terser.set(txaPath + "-21", "AC"); // Active
            } else if (attachment.hasUrl()) {
                terser.set(txaPath + "-21", "AR"); // Archived
            }
        }

        // TXA-22 Document Change Reason
        if (docRef.hasDescription()) {
            terser.set(txaPath + "-22", docRef.getDescription());
        }

        txaIndex++;
    }
}
