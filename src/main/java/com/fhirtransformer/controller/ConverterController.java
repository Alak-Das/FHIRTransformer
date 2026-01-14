package com.fhirtransformer.controller;

import com.fhirtransformer.model.TransactionRecord;
import com.fhirtransformer.repository.TransactionRepository;
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
import java.security.Principal;
import java.time.LocalDateTime;
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
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routingkey}")
    private String routingKey;

    @Value("${app.rabbitmq.fhir.exchange}")
    private String fhirExchange;

    @Value("${app.rabbitmq.fhir.routingkey}")
    private String fhirRoutingKey;

    @Autowired
    public ConverterController(Hl7ToFhirService hl7ToFhirService, FhirToHl7Service fhirToHl7Service,
            RabbitTemplate rabbitTemplate, TransactionRepository transactionRepository) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.fhirToHl7Service = fhirToHl7Service;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionRepository = transactionRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping(value = "/v2-to-fhir", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhir(@RequestBody String hl7Message, Principal principal) {
        String transactionId = null;
        try {
            String[] result = ensureHl7TransactionId(hl7Message);
            String processedMessage = result[0];
            transactionId = result[1];

            logTransaction(principal.getName(), transactionId, "V2_TO_FHIR_ASYNC", "QUEUED");

            rabbitTemplate.convertAndSend(exchange, routingKey, processedMessage);

            Map<String, String> response = new HashMap<>();
            response.put("status", "Accepted");
            response.put("message", "Processing asynchronously");
            response.put("transactionId", transactionId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            logTransaction(principal != null ? principal.getName() : "UNKNOWN", transactionId, "V2_TO_FHIR_ASYNC",
                    "FAILED");
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"Failed to process message: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/v2-to-fhir-sync", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhirSync(@RequestBody String hl7Message, Principal principal) {
        String transactionId = null;
        try {
            String[] result = ensureHl7TransactionId(hl7Message);
            String processedMessage = result[0];
            transactionId = result[1];

            String fhirJson = hl7ToFhirService.convertHl7ToFhir(processedMessage);

            logTransaction(principal.getName(), transactionId, "V2_TO_FHIR_SYNC", "COMPLETED");

            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            logTransaction(principal != null ? principal.getName() : "UNKNOWN", transactionId, "V2_TO_FHIR_SYNC",
                    "FAILED");
            return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/fhir-to-v2", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToHl7(@RequestBody String fhirJson, Principal principal) {
        String transactionId = null;
        try {
            String[] result = ensureFhirTransactionId(fhirJson);
            String processedJson = result[0];
            transactionId = result[1];

            logTransaction(principal.getName(), transactionId, "FHIR_TO_V2_ASYNC", "QUEUED");

            rabbitTemplate.convertAndSend(fhirExchange, fhirRoutingKey, processedJson);

            Map<String, String> response = new HashMap<>();
            response.put("status", "Accepted");
            response.put("message", "Processing asynchronously");
            response.put("transactionId", transactionId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            logTransaction(principal != null ? principal.getName() : "UNKNOWN", transactionId, "FHIR_TO_V2_ASYNC",
                    "FAILED");
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"Failed to process message: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/fhir-to-v2-sync", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertToHl7Sync(@RequestBody String fhirJson, Principal principal) {
        String transactionId = null;
        try {
            String[] result = ensureFhirTransactionId(fhirJson);
            String processedJson = result[0];
            transactionId = result[1];

            String hl7Message = fhirToHl7Service.convertFhirToHl7(processedJson);

            logTransaction(principal.getName(), transactionId, "FHIR_TO_V2_SYNC", "COMPLETED");

            return ResponseEntity.ok(hl7Message);
        } catch (Exception e) {
            logTransaction(principal != null ? principal.getName() : "UNKNOWN", transactionId, "FHIR_TO_V2_SYNC",
                    "FAILED");
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    private void logTransaction(String tenantId, String transactionId, String type, String status) {
        try {
            TransactionRecord record = new TransactionRecord();
            record.setTenantId(tenantId);
            record.setTransactionId(transactionId);
            record.setMessageType(type);
            record.setStatus(status);
            record.setTimestamp(LocalDateTime.now());
            transactionRepository.save(record);
        } catch (Exception e) {
            // Log error but don't fail the request
            System.err.println("Failed to save transaction log: " + e.getMessage());
        }
    }

    private String[] ensureHl7TransactionId(String hl7Message) {
        String[] segments = hl7Message.split("\r");
        String mshSegment = segments[0];
        String[] mshFields = mshSegment.split("\\|", -1);

        String transactionId;
        boolean idGenerated = false;

        if (mshFields.length > 9 && !mshFields[9].isEmpty()) {
            transactionId = mshFields[9];
        } else {
            transactionId = UUID.randomUUID().toString();
            idGenerated = true; // Flag that we need to inject it
        }

        if (!idGenerated) {
            return new String[] { hl7Message, transactionId };
        }

        // Reconstruct MSH with new ID
        List<String> fieldList = new ArrayList<>(Arrays.asList(mshFields));

        while (fieldList.size() <= 9) {
            fieldList.add("");
        }

        fieldList.set(9, transactionId);

        String newMshSegment = String.join("|", fieldList);
        segments[0] = newMshSegment;

        return new String[] { String.join("\r", segments), transactionId };
    }

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
