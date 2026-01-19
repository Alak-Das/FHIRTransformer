package com.al.fhirhl7transformer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.al.fhirhl7transformer.dto.EnrichedMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class MessageEnrichmentService {

    private final ObjectMapper objectMapper;

    public MessageEnrichmentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EnrichedMessage ensureHl7TransactionId(String hl7Message) {
        String[] segments = hl7Message.split("\r");
        String mshSegment = segments[0];
        String[] mshFields = mshSegment.split("\\|", -1);

        String transactionId;
        boolean idGenerated = false;

        // MSH-10 is at index 9 (since splitted array is 0-indexed and MSH has | separators)
        // MSH|^~\&|SendingApp|...|...|...|...|...|MsgId|...
        // Field 0 = MSH
        // Field 1 = ^~\&
        // ...
        // Actually, MSH-10 is indeed the 10th pipe-delimited value if we count MSH as the first one?
        // Let's verify the existing logic.
        // The existing logic says: if (mshFields.length > 9 && !mshFields[9].isEmpty())
        // mshFields[0] is "MSH" (usually, assuming split works that way on "MSH|...")
        // If message is "MSH|^~\&|...", split("|") -> ["MSH", "^~\&", ...]
        // Index 9 is the 10th element. MSH-10 is the Message Control ID.
        // So this logic seems correct for MSH-10.

        if (mshFields.length > 9 && !mshFields[9].isEmpty()) {
            transactionId = mshFields[9];
        } else {
            transactionId = UUID.randomUUID().toString();
            idGenerated = true;
        }

        if (!idGenerated) {
            return new EnrichedMessage(hl7Message, transactionId);
        }

        // Reconstruct MSH with new ID
        List<String> fieldList = new ArrayList<>(Arrays.asList(mshFields));

        while (fieldList.size() <= 9) {
            fieldList.add("");
        }

        fieldList.set(9, transactionId);

        String newMshSegment = String.join("|", fieldList);
        segments[0] = newMshSegment;

        return new EnrichedMessage(String.join("\r", segments), transactionId);
    }

    public EnrichedMessage ensureFhirTransactionId(String fhirJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(fhirJson);
        String transactionId;

        if (root.has("id") && !root.get("id").asText().isEmpty()) {
            transactionId = root.get("id").asText();
            return new EnrichedMessage(fhirJson, transactionId);
        } else {
            transactionId = UUID.randomUUID().toString();
            if (root.isObject()) {
                ((ObjectNode) root).put("id", transactionId);
            }
            return new EnrichedMessage(objectMapper.writeValueAsString(root), transactionId);
        }
    }
}
