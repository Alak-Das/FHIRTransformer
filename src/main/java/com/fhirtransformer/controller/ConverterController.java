package com.fhirtransformer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhirtransformer.dto.EnrichedMessage;
import com.fhirtransformer.model.enums.MessageType;
import com.fhirtransformer.model.enums.TransactionStatus;
import com.fhirtransformer.service.AuditService;
import com.fhirtransformer.service.FhirToHl7Service;
import com.fhirtransformer.service.Hl7ToFhirService;
import com.fhirtransformer.service.MessageEnrichmentService;
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
    public ResponseEntity<String> convertToFhir(@RequestBody String hl7Message, Principal principal) throws Exception {
        EnrichedMessage enriched = messageEnrichmentService.ensureHl7TransactionId(hl7Message);
        String transactionId = enriched.getTransactionId();
        String processedMessage = enriched.getContent();

        auditService.logTransaction(getTenantId(principal), transactionId,
                MessageType.V2_TO_FHIR_ASYNC, TransactionStatus.ACCEPTED);

        rabbitTemplate.convertAndSend(exchange, routingKey, processedMessage);

        return createAcceptedResponse(transactionId);
    }

    @PostMapping(value = "/v2-to-fhir-sync", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhirSync(@RequestBody String hl7Message, Principal principal)
            throws Exception {
        EnrichedMessage enriched = messageEnrichmentService.ensureHl7TransactionId(hl7Message);
        String transactionId = enriched.getTransactionId();
        String processedMessage = enriched.getContent();

        String fhirJson = hl7ToFhirService.convertHl7ToFhir(processedMessage);

        auditService.logTransaction(getTenantId(principal), transactionId,
                MessageType.V2_TO_FHIR_SYNC, TransactionStatus.COMPLETED);

        return ResponseEntity.ok(fhirJson);
    }

    @PostMapping(value = "/fhir-to-v2", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToHl7(@RequestBody String fhirJson, Principal principal) throws Exception {
        EnrichedMessage enriched = messageEnrichmentService.ensureFhirTransactionId(fhirJson);
        String transactionId = enriched.getTransactionId();
        String processedJson = enriched.getContent();

        auditService.logTransaction(getTenantId(principal), transactionId,
                MessageType.FHIR_TO_V2_ASYNC, TransactionStatus.QUEUED);

        rabbitTemplate.convertAndSend(fhirExchange, fhirRoutingKey, processedJson);

        return createAcceptedResponse(transactionId);
    }

    @PostMapping(value = "/fhir-to-v2-sync", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertToHl7Sync(@RequestBody String fhirJson, Principal principal) throws Exception {
        EnrichedMessage enriched = messageEnrichmentService.ensureFhirTransactionId(fhirJson);
        String transactionId = enriched.getTransactionId();
        String processedJson = enriched.getContent();

        String hl7Message = fhirToHl7Service.convertFhirToHl7(processedJson);

        auditService.logTransaction(getTenantId(principal), transactionId,
                MessageType.FHIR_TO_V2_SYNC, TransactionStatus.COMPLETED);

        return ResponseEntity.ok(hl7Message);
    }

    private String getTenantId(Principal principal) {
        return principal != null ? principal.getName() : "UNKNOWN";
    }

    private ResponseEntity<String> createAcceptedResponse(String transactionId) throws Exception {
        Map<String, String> response = new HashMap<>();
        response.put("status", "Accepted");
        response.put("message", "Processing asynchronously");
        response.put("transactionId", transactionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(objectMapper.writeValueAsString(response));
    }
}
