package com.al.fhirhl7transformer.service.converter;

import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Converts HL7 MSH (Message Header) segment to FHIR MessageHeader resource.
 * Provides explicit message control information in FHIR format.
 */
@Slf4j
@Component
public class MessageHeaderConverter implements SegmentConverter<MessageHeader> {

    private final SimpleDateFormat hl7DateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public List<MessageHeader> convert(Terser terser, Bundle bundle, ConversionContext context) {
        try {
            MessageHeader messageHeader = new MessageHeader();
            messageHeader.setId(java.util.UUID.randomUUID().toString());

            // MSH-3 Sending Application
            String sendingApp = terser.get("MSH-3-1");
            String sendingAppOid = terser.get("MSH-3-2");
            if (sendingApp != null && !sendingApp.isEmpty()) {
                MessageHeader.MessageSourceComponent source = new MessageHeader.MessageSourceComponent();
                source.setName(sendingApp);
                if (sendingAppOid != null && !sendingAppOid.isEmpty()) {
                    source.setEndpoint("urn:oid:" + sendingAppOid);
                } else {
                    source.setEndpoint("urn:hl7:application:" + sendingApp);
                }

                // MSH-4 Sending Facility
                String sendingFacility = terser.get("MSH-4-1");
                if (sendingFacility != null && !sendingFacility.isEmpty()) {
                    source.setSoftware(sendingFacility);
                }

                messageHeader.setSource(source);
            }

            // MSH-5 Receiving Application -> destination
            String receivingApp = terser.get("MSH-5-1");
            String receivingAppOid = terser.get("MSH-5-2");
            // MSH-6 Receiving Facility
            String receivingFacility = terser.get("MSH-6-1");

            if (receivingApp != null && !receivingApp.isEmpty()) {
                MessageHeader.MessageDestinationComponent dest = new MessageHeader.MessageDestinationComponent();
                dest.setName(receivingApp);
                if (receivingAppOid != null && !receivingAppOid.isEmpty()) {
                    dest.setEndpoint("urn:oid:" + receivingAppOid);
                } else {
                    dest.setEndpoint("urn:hl7:application:" + receivingApp);
                }

                if (receivingFacility != null && !receivingFacility.isEmpty()) {
                    dest.setReceiver(new Reference()
                            .setDisplay(receivingFacility)
                            .setReference("Organization/" + java.util.UUID.randomUUID().toString()));
                }

                messageHeader.addDestination(dest);
            }

            // MSH-7 Date/Time of Message
            String msgDateTime = terser.get("MSH-7");
            if (msgDateTime != null && !msgDateTime.isEmpty()) {
                try {
                    Date timestamp = hl7DateTimeFormat
                            .parse(msgDateTime.substring(0, Math.min(14, msgDateTime.length())));
                    messageHeader.addExtension()
                            .setUrl("http://hl7.org/fhir/StructureDefinition/message-timestamp")
                            .setValue(new DateTimeType(timestamp));
                } catch (ParseException e) {
                    log.debug("Could not parse message datetime: {}", msgDateTime);
                }
            }

            // MSH-9 Message Type -> eventCoding
            String msgType = terser.get("MSH-9-1");
            String triggerEvent = terser.get("MSH-9-2");
            String msgStructure = terser.get("MSH-9-3");

            if (msgType != null && !msgType.isEmpty()) {
                Coding eventCoding = new Coding();
                eventCoding.setSystem("http://terminology.hl7.org/CodeSystem/v2-0076");
                eventCoding.setCode(msgType + (triggerEvent != null ? "^" + triggerEvent : ""));
                if (msgStructure != null && !msgStructure.isEmpty()) {
                    eventCoding.setDisplay(msgStructure);
                }
                messageHeader.setEvent(eventCoding);
            }

            // MSH-10 Message Control ID
            String messageControlId = terser.get("MSH-10");
            if (messageControlId != null && !messageControlId.isEmpty()) {
                messageHeader.addExtension()
                        .setUrl("http://hl7.org/fhir/StructureDefinition/message-control-id")
                        .setValue(new StringType(messageControlId));
            }

            // MSH-11 Processing ID
            String processingId = terser.get("MSH-11-1");
            if (processingId != null && !processingId.isEmpty()) {
                messageHeader.addExtension()
                        .setUrl("http://hl7.org/fhir/StructureDefinition/processing-id")
                        .setValue(new StringType(processingId));
            }

            // MSH-12 Version ID
            String versionId = terser.get("MSH-12-1");
            if (versionId != null && !versionId.isEmpty()) {
                messageHeader.addExtension()
                        .setUrl("http://hl7.org/fhir/StructureDefinition/hl7-version")
                        .setValue(new StringType(versionId));
            }

            // MSH-18/19 Character Set / Principal Language
            String principalLanguage = terser.get("MSH-19-1");
            if (principalLanguage != null && !principalLanguage.isEmpty()) {
                messageHeader.setLanguage(principalLanguage);
            }

            // MSH-21 Message Profile Identifier
            String profileId = terser.get("MSH-21-1");
            if (profileId != null && !profileId.isEmpty()) {
                messageHeader.getMeta()
                        .addProfile("urn:hl7:profile:" + profileId);
            }

            // MSH-22 Sending Responsible Organization
            String sendingOrg = terser.get("MSH-22-1");
            if (sendingOrg != null && !sendingOrg.isEmpty()) {
                messageHeader.setSender(new Reference()
                        .setReference("Organization/" + java.util.UUID.randomUUID().toString())
                        .setDisplay(sendingOrg));
            }

            // MSH-23 Receiving Responsible Organization
            String receivingOrg = terser.get("MSH-23-1");
            if (receivingOrg != null && !receivingOrg.isEmpty()) {
                messageHeader.setResponsible(new Reference()
                        .setReference("Organization/" + java.util.UUID.randomUUID().toString())
                        .setDisplay(receivingOrg));
            }

            return Collections.singletonList(messageHeader);
        } catch (Exception e) {
            log.error("Error converting MSH to MessageHeader", e);
            return Collections.emptyList();
        }
    }
}
