package com.al.fhirhl7transformer.service.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Converts FHIR MessageHeader to HL7 MSH (Message Header) segment.
 * Provides explicit message control from FHIR MessageHeader resource.
 */
@Component
public class MessageHeaderToMshConverter implements FhirToHl7Converter<MessageHeader> {

    private final SimpleDateFormat hl7DateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public boolean canConvert(Resource resource) {
        return resource instanceof MessageHeader;
    }

    @Override
    public void convert(MessageHeader messageHeader, Message message, Terser terser) throws HL7Exception {
        // MSH-3 Sending Application
        if (messageHeader.hasSource()) {
            MessageHeader.MessageSourceComponent source = messageHeader.getSource();
            if (source.hasName()) {
                terser.set("MSH-3-1", source.getName());
            }
            if (source.hasEndpoint()) {
                String endpoint = source.getEndpoint();
                if (endpoint.startsWith("urn:oid:")) {
                    terser.set("MSH-3-2", endpoint.substring(8));
                }
            }
            // MSH-4 Sending Facility (from software or extension)
            if (source.hasSoftware()) {
                terser.set("MSH-4-1", source.getSoftware());
            }
        }

        // MSH-5 Receiving Application
        // MSH-6 Receiving Facility
        if (messageHeader.hasDestination()) {
            MessageHeader.MessageDestinationComponent dest = messageHeader.getDestinationFirstRep();
            if (dest.hasName()) {
                terser.set("MSH-5-1", dest.getName());
            }
            if (dest.hasEndpoint()) {
                String endpoint = dest.getEndpoint();
                if (endpoint.startsWith("urn:oid:")) {
                    terser.set("MSH-5-2", endpoint.substring(8));
                }
            }
            if (dest.hasReceiver() && dest.getReceiver().hasDisplay()) {
                terser.set("MSH-6-1", dest.getReceiver().getDisplay());
            }
        }

        // MSH-7 Date/Time of Message (from extension or use current time)
        Date messageTime = new Date();
        if (messageHeader.hasExtension()) {
            for (Extension ext : messageHeader.getExtension()) {
                if (ext.getUrl().contains("message-timestamp") && ext.hasValue()) {
                    if (ext.getValue() instanceof DateTimeType) {
                        messageTime = ((DateTimeType) ext.getValue()).getValue();
                    }
                    break;
                }
            }
        }
        terser.set("MSH-7", hl7DateTimeFormat.format(messageTime));

        // MSH-9 Message Type (from eventCoding)
        if (messageHeader.hasEventCoding()) {
            Coding event = messageHeader.getEventCoding();
            if (event.hasCode()) {
                String code = event.getCode();
                if (code.contains("^")) {
                    String[] parts = code.split("\\^", 2);
                    terser.set("MSH-9-1", parts[0]);
                    terser.set("MSH-9-2", parts[1]);
                } else {
                    terser.set("MSH-9-1", code);
                }
            }
            if (event.hasDisplay()) {
                terser.set("MSH-9-3", event.getDisplay());
            }
        }

        // MSH-10 Message Control ID (from extension)
        if (messageHeader.hasExtension()) {
            for (Extension ext : messageHeader.getExtension()) {
                if (ext.getUrl().contains("message-control-id") && ext.hasValue()) {
                    terser.set("MSH-10", ((StringType) ext.getValue()).getValue());
                    break;
                }
            }
        }
        // If no extension, use MessageHeader ID
        if (terser.get("MSH-10") == null && messageHeader.hasId()) {
            terser.set("MSH-10", messageHeader.getIdElement().getIdPart());
        }

        // MSH-11 Processing ID (from extension)
        if (messageHeader.hasExtension()) {
            for (Extension ext : messageHeader.getExtension()) {
                if (ext.getUrl().contains("processing-id") && ext.hasValue()) {
                    terser.set("MSH-11-1", ((StringType) ext.getValue()).getValue());
                    break;
                }
            }
        }

        // MSH-12 Version ID (from extension)
        if (messageHeader.hasExtension()) {
            for (Extension ext : messageHeader.getExtension()) {
                if (ext.getUrl().contains("hl7-version") && ext.hasValue()) {
                    terser.set("MSH-12-1", ((StringType) ext.getValue()).getValue());
                    break;
                }
            }
        }

        // MSH-15 Accept Acknowledgment Type
        // MSH-16 Application Acknowledgment Type
        if (messageHeader.hasExtension()) {
            for (Extension ext : messageHeader.getExtension()) {
                if (ext.getUrl().contains("accept-ack-type") && ext.hasValue()) {
                    terser.set("MSH-15", ((StringType) ext.getValue()).getValue());
                } else if (ext.getUrl().contains("app-ack-type") && ext.hasValue()) {
                    terser.set("MSH-16", ((StringType) ext.getValue()).getValue());
                } else if (ext.getUrl().contains("country-code") && ext.hasValue()) {
                    terser.set("MSH-17", ((StringType) ext.getValue()).getValue());
                }
            }
        }

        // MSH-18/19 Character Set / Principal Language
        if (messageHeader.hasLanguage()) {
            terser.set("MSH-19-1", messageHeader.getLanguage());
        }

        // MSH-21 Message Profile Identifier
        if (messageHeader.hasMeta() && messageHeader.getMeta().hasProfile()) {
            String profile = messageHeader.getMeta().getProfile().get(0).getValue();
            if (profile.startsWith("urn:hl7:profile:")) {
                terser.set("MSH-21-1", profile.substring(16));
            }
        }

        // MSH-22 Sending Responsible Organization
        if (messageHeader.hasSender()) {
            Reference sender = messageHeader.getSender();
            if (sender.hasDisplay()) {
                terser.set("MSH-22-1", sender.getDisplay());
            }
        }

        // MSH-23 Receiving Responsible Organization
        if (messageHeader.hasResponsible()) {
            Reference responsible = messageHeader.getResponsible();
            if (responsible.hasDisplay()) {
                terser.set("MSH-23-1", responsible.getDisplay());
            }
        }
    }
}
