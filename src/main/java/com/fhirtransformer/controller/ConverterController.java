package com.fhirtransformer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhirtransformer.dto.EnrichedMessage;
import com.fhirtransformer.model.enums.MessageType;
import com.fhirtransformer.model.enums.TransactionStatus;
import com.fhirtransformer.service.AuditService;
import com.fhirtransformer.service.FhirToHl7Service;
import com.fhirtransformer.service.Hl7ToFhirService;
import com.fhirtransformer.service.MessageEnrichmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/convert")
public class ConverterController {

    private static final Logger log = LoggerFactory.getLogger(ConverterController.class);

    private final Hl7ToFhirService hl7ToFhirService;
    private final FhirToHl7Service fhirToHl7Service;
    private final RabbitTemplate rabbitTemplate;
    private final MessageEnrichmentService messageEnrichmentService;
    private final AuditService auditService;
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
    public ConverterController(Hl7ToFhirService hl7ToFhirService,
                               FhirToHl7Service fhirToHl7Service,
                               RabbitTemplate rabbitTemplate,
                               MessageEnrichmentService messageEnrichmentService,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.fhirToHl7Service = fhirToHl7Service;
        this.rabbitTemplate = rabbitTemplate;
        this.messageEnrichmentService = messageEnrichmentService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/v2-to-fhir", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhir(@RequestBody String hl7Message, Principal principal) {
        String transactionId = null;
        try {
            EnrichedMessage enriched = messageEnrichmentService.ensureHl7TransactionId(hl7Message);
            transactionId = enriched.getTransactionId();
            String processedMessage = enriched.getContent();

            auditService.logTransaction(getTenantId(principal), transactionId,
                    MessageType.V2_TO_FHIR_ASYNC, TransactionStatus.ACCEPTED);

            rabbitTemplate.convertAndSend(exchange, routingKey, processedMessage);

            return createAcceptedResponse(transactionId);
        } catch (Exception e) {
            handleError(getTenantId(principal), transactionId, MessageType.V2_TO_FHIR_ASYNC, e);
            return createErrorResponse(e.getMessage());
        }
    }

    @PostMapping(value = "/v2-to-fhir-sync", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhirSync(@RequestBody String hl7Message, Principal principal) {
        String transactionId = null;
        try {
            EnrichedMessage enriched = messageEnrichmentService.ensureHl7TransactionId(hl7Message);
            transactionId = enriched.getTransactionId();
            String processedMessage = enriched.getContent();

            String fhirJson = hl7ToFhirService.convertHl7ToFhir(processedMessage);

            auditService.logTransaction(getTenantId(principal), transactionId,
                    MessageType.V2_TO_FHIR_SYNC, TransactionStatus.COMPLETED);

            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            handleError(getTenantId(principal), transactionId, MessageType.V2_TO_FHIR_SYNC, e);
            return createErrorResponse(e.getMessage());
        }
    }

    @PostMapping(value = "/fhir-to-v2", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToHl7(@RequestBody String fhirJson, Principal principal) {
        String transactionId = null;
        try {
            EnrichedMessage enriched = messageEnrichmentService.ensureFhirTransactionId(fhirJson);
            transactionId = enriched.getTransactionId();
            String processedJson = enriched.getContent();

            auditService.logTransaction(getTenantId(principal), transactionId,
                    MessageType.FHIR_TO_V2_ASYNC, TransactionStatus.QUEUED);

            rabbitTemplate.convertAndSend(fhirExchange, fhirRoutingKey, processedJson);

            return createAcceptedResponse(transactionId);
        } catch (Exception e) {
            handleError(getTenantId(principal), transactionId, MessageType.FHIR_TO_V2_ASYNC, e);
            return createErrorResponse(e.getMessage());
        }
    }

    @PostMapping(value = "/fhir-to-v2-sync", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertToHl7Sync(@RequestBody String fhirJson, Principal principal) {
        String transactionId = null;
        try {
            EnrichedMessage enriched = messageEnrichmentService.ensureFhirTransactionId(fhirJson);
            transactionId = enriched.getTransactionId();
            String processedJson = enriched.getContent();

            String hl7Message = fhirToHl7Service.convertFhirToHl7(processedJson);

            auditService.logTransaction(getTenantId(principal), transactionId,
                    MessageType.FHIR_TO_V2_SYNC, TransactionStatus.COMPLETED);

            return ResponseEntity.ok(hl7Message);
        } catch (Exception e) {
            handleError(getTenantId(principal), transactionId, MessageType.FHIR_TO_V2_SYNC, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    private String getTenantId(Principal principal) {
        return principal != null ? principal.getName() : "UNKNOWN";
    }

    private void handleError(String tenantId, String transactionId, MessageType type, Exception e) {
        log.error("Error processing transaction {}: {}", transactionId, e.getMessage());
        auditService.logTransaction(tenantId, transactionId, type, TransactionStatus.FAILED);
    }

    private ResponseEntity<String> createAcceptedResponse(String transactionId) throws Exception {
        Map<String, String> response = new HashMap<>();
        response.put("status", "Accepted");
        response.put("message", "Processing asynchronously");
        response.put("transactionId", transactionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(objectMapper.writeValueAsString(response));
    }

    private ResponseEntity<String> createErrorResponse(String errorMessage) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to process message");
            error.put("details", errorMessage);
            return ResponseEntity.badRequest().body(objectMapper.writeValueAsString(error));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"Internal handling error\"}");
        }
    }
}
