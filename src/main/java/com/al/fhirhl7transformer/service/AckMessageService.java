package com.al.fhirhl7transformer.service;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ACK;
import ca.uhn.hl7v2.model.v25.segment.ERR;
import ca.uhn.hl7v2.model.v25.segment.MSA;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating HL7 ACK (Acknowledgment) messages.
 * 
 * ACK Types:
 * - AA (Application Accept): Message processed successfully
 * - AE (Application Error): Message accepted but processing failed
 * - AR (Application Reject): Message rejected due to invalid structure
 */
@Service
@Slf4j
public class AckMessageService {

    private final HapiContext hl7Context;

    @Autowired
    public AckMessageService(HapiContext hl7Context) {
        this.hl7Context = hl7Context;
    }

    /**
     * Generate an ACK message for a successfully processed HL7 message.
     *
     * @param originalMessage The original HL7 message that was processed
     * @return The ACK message as a pipe-delimited string
     */
    public String generateAckAccept(String originalMessage) throws HL7Exception {
        return generateAck(originalMessage, "AA", null);
    }

    /**
     * Generate an ACK message for a message that was accepted but had processing
     * errors.
     *
     * @param originalMessage The original HL7 message
     * @param errorMessage    Description of the error that occurred
     * @return The ACK message as a pipe-delimited string
     */
    public String generateAckError(String originalMessage, String errorMessage) throws HL7Exception {
        return generateAck(originalMessage, "AE", errorMessage);
    }

    /**
     * Generate an ACK message for a rejected message (invalid structure).
     *
     * @param originalMessage The original HL7 message
     * @param errorMessage    Description of why the message was rejected
     * @return The ACK message as a pipe-delimited string
     */
    public String generateAckReject(String originalMessage, String errorMessage) throws HL7Exception {
        return generateAck(originalMessage, "AR", errorMessage);
    }

    /**
     * Generate an ACK message with the specified acknowledgment code.
     *
     * @param originalMessage The original HL7 message
     * @param ackCode         The acknowledgment code (AA, AE, AR)
     * @param errorMessage    Optional error message (for AE/AR)
     * @return The ACK message as a pipe-delimited string
     */
    public String generateAck(String originalMessage, String ackCode, String errorMessage) throws HL7Exception {
        Parser parser = hl7Context.getPipeParser();
        Message origMsg;

        try {
            origMsg = parser.parse(originalMessage);
        } catch (HL7Exception e) {
            // If we can't parse the original, create a minimal ACK
            log.warn("Could not parse original message for ACK generation: {}", e.getMessage());
            return generateMinimalAck(ackCode, errorMessage);
        }

        Terser origTerser = new Terser(origMsg);

        // Create ACK message
        ACK ack = new ACK();

        // Populate MSH segment
        MSH msh = ack.getMSH();
        msh.getFieldSeparator().setValue("|");
        msh.getEncodingCharacters().setValue("^~\\&");

        // Swap sending and receiving applications/facilities
        String origSendingApp = origTerser.get("/.MSH-3");
        String origSendingFacility = origTerser.get("/.MSH-4");
        String origReceivingApp = origTerser.get("/.MSH-5");
        String origReceivingFacility = origTerser.get("/.MSH-6");

        msh.getSendingApplication().getNamespaceID().setValue(
                origReceivingApp != null ? origReceivingApp : "FHIR-TRANSFORMER");
        msh.getSendingFacility().getNamespaceID().setValue(
                origReceivingFacility != null ? origReceivingFacility : "TRANSFORM-FACILITY");
        msh.getReceivingApplication().getNamespaceID().setValue(
                origSendingApp != null ? origSendingApp : "");
        msh.getReceivingFacility().getNamespaceID().setValue(
                origSendingFacility != null ? origSendingFacility : "");

        // Set message timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        msh.getDateTimeOfMessage().getTime().setValue(sdf.format(new Date()));

        // Set message type to ACK
        String origTrigger = origTerser.get("/.MSH-9-2");
        msh.getMessageType().getMessageCode().setValue("ACK");
        msh.getMessageType().getTriggerEvent().setValue(origTrigger != null ? origTrigger : "");
        msh.getMessageType().getMessageStructure().setValue("ACK");

        // Set message control ID (unique for ACK)
        msh.getMessageControlID().setValue("ACK-" + UUID.randomUUID().toString().substring(0, 8));

        // Set processing ID
        String origProcessingId = origTerser.get("/.MSH-11");
        msh.getProcessingID().getProcessingID().setValue(
                origProcessingId != null ? origProcessingId : "P");

        // Set version ID
        String origVersion = origTerser.get("/.MSH-12");
        msh.getVersionID().getVersionID().setValue(
                origVersion != null ? origVersion : "2.5");

        // Populate MSA segment (Message Acknowledgment)
        MSA msa = ack.getMSA();
        msa.getAcknowledgmentCode().setValue(ackCode);

        // Reference the original message control ID
        String origMsgControlId = origTerser.get("/.MSH-10");
        msa.getMessageControlID().setValue(origMsgControlId != null ? origMsgControlId : "UNKNOWN");

        // Add text message for errors
        if (errorMessage != null && !errorMessage.isEmpty()) {
            msa.getTextMessage().setValue(truncateMessage(errorMessage, 80));
        }

        // Add ERR segment for errors
        if (("AE".equals(ackCode) || "AR".equals(ackCode)) && errorMessage != null) {
            ERR err = ack.getERR();
            err.getErrorCodeAndLocation(0).getSegmentID().setValue("MSH");
            err.getErrorCodeAndLocation(0).getCodeIdentifyingError().getIdentifier().setValue(
                    "AR".equals(ackCode) ? "100" : "200");
            err.getErrorCodeAndLocation(0).getCodeIdentifyingError().getText().setValue(
                    truncateMessage(errorMessage, 200));
        }

        String ackMessage = parser.encode(ack);
        log.info("Generated ACK message with code {} for original message {}", ackCode, origMsgControlId);

        return ackMessage;
    }

    /**
     * Generate a minimal ACK when the original message cannot be parsed.
     */
    private String generateMinimalAck(String ackCode, String errorMessage) throws HL7Exception {
        ACK ack = new ACK();

        MSH msh = ack.getMSH();
        msh.getFieldSeparator().setValue("|");
        msh.getEncodingCharacters().setValue("^~\\&");
        msh.getSendingApplication().getNamespaceID().setValue("FHIR-TRANSFORMER");
        msh.getSendingFacility().getNamespaceID().setValue("TRANSFORM-FACILITY");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        msh.getDateTimeOfMessage().getTime().setValue(sdf.format(new Date()));
        msh.getMessageType().getMessageCode().setValue("ACK");
        msh.getMessageType().getMessageStructure().setValue("ACK");
        msh.getMessageControlID().setValue("ACK-" + UUID.randomUUID().toString().substring(0, 8));
        msh.getProcessingID().getProcessingID().setValue("P");
        msh.getVersionID().getVersionID().setValue("2.5");

        MSA msa = ack.getMSA();
        msa.getAcknowledgmentCode().setValue(ackCode);
        msa.getMessageControlID().setValue("UNKNOWN");

        if (errorMessage != null) {
            msa.getTextMessage().setValue(truncateMessage(errorMessage, 80));
        }

        return hl7Context.getPipeParser().encode(ack);
    }

    /**
     * Truncate a message to fit within HL7 field limits.
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null)
            return null;
        if (message.length() <= maxLength)
            return message;
        return message.substring(0, maxLength - 3) + "...";
    }
}
