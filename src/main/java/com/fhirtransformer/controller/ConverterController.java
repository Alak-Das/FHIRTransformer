package com.fhirtransformer.controller;

import com.fhirtransformer.service.Hl7ToFhirService;
import com.fhirtransformer.service.FhirToHl7Service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@RestController
@RequestMapping("/api/convert")
public class ConverterController {

    private final Hl7ToFhirService hl7ToFhirService;
    private final FhirToHl7Service fhirToHl7Service;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routingkey}")
    private String routingKey;

    @Value("${app.rabbitmq.fhir.exchange}")
    private String fhirExchange;

    @Value("${app.rabbitmq.fhir.routingkey}")
    private String fhirRoutingKey;

    private final ObjectMapper objectMapper;

    @Autowired
    public ConverterController(Hl7ToFhirService hl7ToFhirService, FhirToHl7Service fhirToHl7Service,
            RabbitTemplate rabbitTemplate) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.fhirToHl7Service = fhirToHl7Service;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping(value = "/v2-to-fhir", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhir(@RequestBody String hl7Message) {
        try {
            // Check/Inject Transaction ID (MSH-10)
            String[] result = ensureHl7TransactionId(hl7Message);
            String processedMessage = result[0];
            String transactionId = result[1];

            // Publish to RabbitMQ
            rabbitTemplate.convertAndSend(exchange, routingKey, processedMessage);

            Map<String, String> response = new HashMap<>();
            response.put("status", "Accepted");
            response.put("message", "Processing asynchronously");
            response.put("transactionId", transactionId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"Failed to process message: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/v2-to-fhir-sync", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhirSync(@RequestBody String hl7Message) {
        try {
            String fhirJson = hl7ToFhirService.convertHl7ToFhir(hl7Message);
            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/fhir-to-v2", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToHl7(@RequestBody String fhirJson) {
        try {
            // Check/Inject Transaction ID (Bundle.id)
            String[] result = ensureFhirTransactionId(fhirJson);
            String processedJson = result[0];
            String transactionId = result[1];

            // Publish to RabbitMQ
            rabbitTemplate.convertAndSend(fhirExchange, fhirRoutingKey, processedJson);

            Map<String, String> response = new HashMap<>();
            response.put("status", "Accepted");
            response.put("message", "Processing asynchronously");
            response.put("transactionId", transactionId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"Failed to process message: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/fhir-to-v2-sync", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertToHl7Sync(@RequestBody String fhirJson) {
        try {
            String hl7Message = fhirToHl7Service.convertFhirToHl7(fhirJson);
            return ResponseEntity.ok(hl7Message);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Helper: Ensure HL7 MSH-10 exists
    private String[] ensureHl7TransactionId(String hl7Message) {
        String[] segments = hl7Message.split("\r");
        String mshSegment = segments[0];
        String[] mshFields = mshSegment.split("\\|", -1); // Keep empty trailing fields

        String transactionId;
        boolean idGenerated = false;

        // MSH-10 is at index 9 (0-based, assuming MSH is part of the split if we split
        // by |)
        // Actually MSH fields: MSH (0), Separators (1), App (2)...
        // Standard split on "|" of "MSH|^~\&|..." -> ["MSH", "^~\&", "App", ...]
        // MSH-3 is index 2. MSH-10 is index 9.

        if (mshFields.length > 9 && !mshFields[9].isEmpty()) {
            transactionId = mshFields[9];
        } else {
            transactionId = UUID.randomUUID().toString();
            if (mshFields.length <= 9) {
                // Resize array implies rebuilding logic which is complex with array.
                // Simpler to just use StringBuilder logic for MSH.
            }
            idGenerated = true;
        }

        if (!idGenerated) {
            return new String[] { hl7Message, transactionId };
        }

        // Reconstruct MSH with new ID
        StringBuilder newMsh = new StringBuilder();
        for (int i = 0; i < mshFields.length; i++) {
            if (i == 9) {
                newMsh.append(transactionId);
            } else {
                newMsh.append(mshFields[i]);
            }
            if (i < mshFields.length - 1) {
                newMsh.append("|");
            }
        }

        // Handle case where MSH was too short
        while (newMsh.toString().split("\\|", -1).length <= 10) {
            newMsh.append("|");
            if (newMsh.toString().split("\\|", -1).length == 10) {
                newMsh.append(transactionId);
            }
        }

        // Simplified Logic: Just regex replace or better yet, proper index handling
        // If we really need to inject, let's just do it cleanly.
        // Re-do logic:

        if (idGenerated) {
            // We need to inject transactionId at MSH-10
            // Re-split strictly
            String[] fields = mshSegment.split("\\|");
            List<String> fieldList = new ArrayList<>(Arrays.asList(fields));
            while (fieldList.size() < 10) {
                fieldList.add("");
            }
            if (fieldList.size() == 10) { // Index 9 is the 10th element
                fieldList.add(""); // Move to index 10? No MSH-10 is index 9.
                // Java split: "A|B".split -> [A, B].
                // MSH-1 is logical separator.
                // "MSH|^~\&|App|Fac|...". indices: 0=MSH, 1=^~\&, 2=App... 9=MSH-10.
            }

            // Ensure size
            while (fieldList.size() <= 9) {
                fieldList.add("");
            }
            fieldList.set(9, transactionId);

            String newMshSegment = String.join("|", fieldList);
            segments[0] = newMshSegment;
            return new String[] { String.join("\r", segments), transactionId };
        }

        return new String[] { hl7Message, transactionId };
    }

    // Helper: Ensure FHIR Bundle Key
    private String[] ensureFhirTransactionId(String fhirJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(fhirJson);
        String transactionId;

        if (root.has("id") && !root.get("id").asText().isEmpty()) {
            transactionId = root.get("id").asText();
            return new String[] { fhirJson, transactionId };
        } else {
            transactionId = UUID.randomUUID().toString();
            if (root.isObject()) {
                ((ObjectNode) root).put("id", transactionId);
            }
            return new String[] { objectMapper.writeValueAsString(root), transactionId };
        }
    }
}
