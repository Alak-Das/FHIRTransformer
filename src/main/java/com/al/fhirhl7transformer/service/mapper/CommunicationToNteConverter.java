package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Converts FHIR Communication to HL7 NTE (Notes and Comments) segment.
 */
@Component
public class CommunicationToNteConverter implements FhirToHl7Converter<Communication> {

    private int nteIndex = 0;

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof Communication;
    }

    @Override
    public void convert(Communication communication, Message message, Terser terser) throws HL7Exception {
        // Each payload in Communication becomes an NTE segment
        if (communication.hasPayload()) {
            for (Communication.CommunicationPayloadComponent payload : communication.getPayload()) {
                String ntePath = "/.NTE(" + nteIndex + ")";

                // NTE-1 Set ID
                terser.set(ntePath + "-1", String.valueOf(nteIndex + 1));

                // NTE-2 Source of Comment
                if (communication.hasSender()) {
                    Reference sender = communication.getSender();
                    if (sender.hasReference()) {
                        String ref = sender.getReference();
                        if (ref.contains("Practitioner")) {
                            terser.set(ntePath + "-2", "P"); // Practitioner
                        } else if (ref.contains("Patient")) {
                            terser.set(ntePath + "-2", "L"); // Licensed Provider (closest match)
                        } else {
                            terser.set(ntePath + "-2", "O"); // Other
                        }
                    }
                }

                // NTE-3 Comment (the actual content)
                if (payload.hasContentStringType()) {
                    String content = payload.getContentStringType().getValue();
                    // HL7 NTE-3 is repeating, but we put entire content in first repetition
                    // Split by newlines for multiple NTE-3 entries if needed
                    String[] lines = content.split("\n");
                    for (int i = 0; i < lines.length && i < 10; i++) { // Limit to 10 lines per NTE
                        terser.set(ntePath + "-3(" + i + ")", lines[i].trim());
                    }
                } else if (payload.hasContentAttachment()) {
                    Attachment attachment = payload.getContentAttachment();
                    if (attachment.hasTitle()) {
                        terser.set(ntePath + "-3", "Attachment: " + attachment.getTitle());
                    }
                    if (attachment.hasUrl()) {
                        // Could add URL reference
                    }
                } else if (payload.hasContentReference()) {
                    Reference ref = payload.getContentReference();
                    if (ref.hasDisplay()) {
                        terser.set(ntePath + "-3", ref.getDisplay());
                    } else if (ref.hasReference()) {
                        terser.set(ntePath + "-3", "See: " + ref.getReference());
                    }
                }

                // NTE-4 Comment Type
                if (communication.hasCategory()) {
                    CodeableConcept category = communication.getCategoryFirstRep();
                    if (category.hasCoding()) {
                        Coding coding = category.getCodingFirstRep();
                        terser.set(ntePath + "-4-1", coding.getCode());
                        if (coding.hasDisplay()) {
                            terser.set(ntePath + "-4-2", coding.getDisplay());
                        }
                    } else if (category.hasText()) {
                        terser.set(ntePath + "-4-2", category.getText());
                    }
                } else {
                    // Default comment type based on communication topic
                    if (communication.hasTopic()) {
                        CodeableConcept topic = communication.getTopic();
                        if (topic.hasText()) {
                            terser.set(ntePath + "-4-2", topic.getText());
                        }
                    }
                }

                // NTE-5 Entered By
                if (communication.hasSender()) {
                    Reference sender = communication.getSender();
                    if (sender.hasReference()) {
                        String ref = sender.getReference();
                        if (ref.contains("/")) {
                            terser.set(ntePath + "-5-1", ref.substring(ref.lastIndexOf("/") + 1));
                        }
                    }
                    if (sender.hasDisplay()) {
                        terser.set(ntePath + "-5-2", sender.getDisplay());
                    }
                }

                // NTE-6 Entered Date/Time
                if (communication.hasSent()) {
                    terser.set(ntePath + "-6", new java.text.SimpleDateFormat("yyyyMMddHHmm")
                            .format(communication.getSent()));
                }

                nteIndex++;
            }
        } else if (communication.hasNote()) {
            // Fallback: use note annotations if no payload
            for (Annotation note : communication.getNote()) {
                String ntePath = "/.NTE(" + nteIndex + ")";

                terser.set(ntePath + "-1", String.valueOf(nteIndex + 1));

                if (note.hasText()) {
                    terser.set(ntePath + "-3", note.getText());
                }

                if (note.hasAuthorReference()) {
                    Reference author = note.getAuthorReference();
                    if (author.hasReference()) {
                        String ref = author.getReference();
                        if (ref.contains("/")) {
                            terser.set(ntePath + "-5-1", ref.substring(ref.lastIndexOf("/") + 1));
                        }
                    }
                }

                if (note.hasTime()) {
                    terser.set(ntePath + "-6", new java.text.SimpleDateFormat("yyyyMMddHHmm")
                            .format(note.getTime()));
                }

                nteIndex++;
            }
        }
    }
}
