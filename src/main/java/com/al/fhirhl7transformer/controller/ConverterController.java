package com.al.fhirhl7transformer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.al.fhirhl7transformer.dto.EnrichedMessage;
import com.al.fhirhl7transformer.model.enums.MessageType;
import com.al.fhirhl7transformer.model.enums.TransactionStatus;
import com.al.fhirhl7transformer.service.AuditService;
import com.al.fhirhl7transformer.service.IdempotencyService;
import com.al.fhirhl7transformer.model.TransactionRecord;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.parser.EncodingNotSupportedException;
import com.fasterxml.jackson.core.JsonParseException;
import com.al.fhirhl7transformer.exception.FhirValidationException;
import lombok.extern.slf4j.Slf4j;
import com.al.fhirhl7transformer.service.FhirToHl7Service;
import com.al.fhirhl7transformer.service.Hl7ToFhirService;
import com.al.fhirhl7transformer.service.MessageEnrichmentService;
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

import org.springframework.web.bind.annotation.ExceptionHandler;

import com.al.fhirhl7transformer.dto.BatchConversionResponse;
import com.al.fhirhl7transformer.dto.BatchHl7Request;
import com.al.fhirhl7transformer.service.BatchConversionService;
import com.al.fhirhl7transformer.service.AckMessageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/convert")
@Slf4j
@Tag(name = "Conversion", description = "HL7 v2.x ↔ FHIR R4 bidirectional conversion endpoints")
public class ConverterController {

    private final Hl7ToFhirService hl7ToFhirService;
    private final FhirToHl7Service fhirToHl7Service;
    private final BatchConversionService batchConversionService;
    private final RabbitTemplate rabbitTemplate;
    private final MessageEnrichmentService messageEnrichmentService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final AckMessageService ackMessageService;

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
            BatchConversionService batchConversionService,
            RabbitTemplate rabbitTemplate,
            MessageEnrichmentService messageEnrichmentService,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            AckMessageService ackMessageService) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.fhirToHl7Service = fhirToHl7Service;
        this.batchConversionService = batchConversionService;
        this.rabbitTemplate = rabbitTemplate;
        this.messageEnrichmentService = messageEnrichmentService;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.ackMessageService = ackMessageService;
    }

    @Operation(summary = "Convert HL7 v2 to FHIR (Async)", description = "Queues an HL7 v2.x message for asynchronous conversion to FHIR R4. Returns transaction ID for status tracking.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Message accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid HL7 message format"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @PostMapping(value = "/v2-to-fhir", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhir(
            @Parameter(description = "HL7 v2.x message in pipe-delimited format") @RequestBody String hl7Message,
            @Parameter(description = "Unique key for idempotent requests") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletResponse response,
            Principal principal) throws Exception {

        String tenantId = getTenantId(principal);

        // Check for duplicate request using idempotency key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            java.util.Optional<TransactionRecord> existing = idempotencyService.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                TransactionRecord record = existing.get();
                log.info("Duplicate request detected for idempotency key: {}", idempotencyKey);
                Map<String, String> duplicateResponse = new HashMap<>();
                duplicateResponse.put("status", "Already processed");
                duplicateResponse.put("transactionId", record.getTransactionId());
                duplicateResponse.put("originalStatus", record.getStatus());
                return ResponseEntity.ok(objectMapper.writeValueAsString(duplicateResponse));
            }
        }

        EnrichedMessage enriched = messageEnrichmentService.ensureHl7TransactionId(hl7Message);
        String transactionId = enriched.getTransactionId();
        org.slf4j.MDC.put("transformerId", transactionId); // Update unified ID
        response.setHeader("transformerId", transactionId);
        String processedMessage = enriched.getContent();

        auditService.logTransaction(tenantId, transactionId,
                MessageType.V2_TO_FHIR_ASYNC, TransactionStatus.ACCEPTED, idempotencyKey);

        rabbitTemplate.convertAndSend(exchange, routingKey, processedMessage, message -> {
            message.getMessageProperties().setHeader("tenantId", tenantId);
            return message;
        });

        return createAcceptedResponse(transactionId);
    }

    @Operation(summary = "Convert HL7 v2 to FHIR (Sync)", description = "Synchronously converts an HL7 v2.x message to FHIR R4 Bundle. Returns the converted FHIR JSON directly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful conversion"),
            @ApiResponse(responseCode = "400", description = "Invalid HL7 message or validation error"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @PostMapping(value = "/v2-to-fhir-sync", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhirSync(
            @Parameter(description = "HL7 v2.x message in pipe-delimited format") @RequestBody String hl7Message,
            HttpServletResponse response, Principal principal)
            throws Exception {
        try {
            EnrichedMessage enriched = messageEnrichmentService.ensureHl7TransactionId(hl7Message);
            String transactionId = enriched.getTransactionId();
            org.slf4j.MDC.put("transformerId", transactionId); // Update unified ID
            response.setHeader("transformerId", transactionId);
            String processedMessage = enriched.getContent();

            String fhirJson = hl7ToFhirService.convertHl7ToFhir(processedMessage);

            // Generate ACK message for successful conversion
            String ackMessage = ackMessageService.generateAckAccept(processedMessage);
            response.setHeader("X-HL7-ACK", java.util.Base64.getEncoder().encodeToString(ackMessage.getBytes()));

            auditService.logTransaction(getTenantId(principal), transactionId,
                    MessageType.V2_TO_FHIR_SYNC, TransactionStatus.COMPLETED);

            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            log.error("Error converting HL7 to FHIR: {}", e.getMessage(), e);

            // Generate NAK (Application Error)
            try {
                // Determine if it's a Reject (AR) or Error (AE) based on exception type
                String nakMessage;
                if (e instanceof HL7Exception || e instanceof ca.uhn.fhir.parser.DataFormatException) {
                    nakMessage = ackMessageService.generateAckReject(hl7Message, e.getMessage());
                } else {
                    nakMessage = ackMessageService.generateAckError(hl7Message, e.getMessage());
                }
                response.setHeader("X-HL7-ACK", java.util.Base64.getEncoder().encodeToString(nakMessage.getBytes()));
            } catch (Exception ackEx) {
                log.error("Failed to generate NAK: {}", ackEx.getMessage());
            }

            throw e; // Re-throw to be handled by global exception handler for JSON body response
        }
    }

    @Operation(summary = "Convert FHIR to HL7 v2 (Async)", description = "Queues a FHIR R4 Bundle for asynchronous conversion to HL7 v2.x. Returns transaction ID for status tracking.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Bundle accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid FHIR Bundle format"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @PostMapping(value = "/fhir-to-v2", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToHl7(
            @Parameter(description = "FHIR R4 Bundle in JSON format") @RequestBody String fhirJson,
            @Parameter(description = "Unique key for idempotent requests") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletResponse response,
            Principal principal) throws Exception {

        String tenantId = getTenantId(principal);

        // Check for duplicate request using idempotency key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            java.util.Optional<TransactionRecord> existing = idempotencyService.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                TransactionRecord record = existing.get();
                log.info("Duplicate request detected for idempotency key: {}", idempotencyKey);
                Map<String, String> duplicateResponse = new HashMap<>();
                duplicateResponse.put("status", "Already processed");
                duplicateResponse.put("transactionId", record.getTransactionId());
                duplicateResponse.put("originalStatus", record.getStatus());
                return ResponseEntity.ok(objectMapper.writeValueAsString(duplicateResponse));
            }
        }

        EnrichedMessage enriched = messageEnrichmentService.ensureFhirTransactionId(fhirJson);
        String transactionId = enriched.getTransactionId();
        org.slf4j.MDC.put("transformerId", transactionId); // Update unified ID
        response.setHeader("transformerId", transactionId);
        String processedJson = enriched.getContent();

        auditService.logTransaction(tenantId, transactionId,
                MessageType.FHIR_TO_V2_ASYNC, TransactionStatus.QUEUED, idempotencyKey);

        rabbitTemplate.convertAndSend(fhirExchange, fhirRoutingKey, processedJson);

        return createAcceptedResponse(transactionId);
    }

    @Operation(summary = "Convert FHIR to HL7 v2 (Sync)", description = "Synchronously converts a FHIR R4 Bundle to HL7 v2.x message. Returns the converted HL7 message directly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful conversion"),
            @ApiResponse(responseCode = "400", description = "Invalid FHIR Bundle or validation error"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @PostMapping(value = "/fhir-to-v2-sync", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertToHl7Sync(
            @Parameter(description = "FHIR R4 Bundle in JSON format") @RequestBody String fhirJson,
            HttpServletResponse response, Principal principal) throws Exception {
        EnrichedMessage enriched = messageEnrichmentService.ensureFhirTransactionId(fhirJson);
        String transactionId = enriched.getTransactionId();
        org.slf4j.MDC.put("transformerId", transactionId); // Update unified ID
        response.setHeader("transformerId", transactionId);
        String processedJson = enriched.getContent();

        String hl7Message = fhirToHl7Service.convertFhirToHl7(processedJson);

        auditService.logTransaction(getTenantId(principal), transactionId,
                MessageType.FHIR_TO_V2_SYNC, TransactionStatus.COMPLETED);

        return ResponseEntity.ok(hl7Message);
    }

    /**
     * Batch convert multiple HL7 messages to FHIR in parallel.
     * 
     * @param request   Batch request containing list of HL7 messages
     * @param principal Authenticated user
     * @return BatchConversionResponse with results and errors
     */
    @PostMapping(value = "/v2-to-fhir-batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchConversionResponse> convertHl7ToFhirBatch(
            @RequestBody @jakarta.validation.Valid BatchHl7Request request,
            Principal principal) {

        String tenantId = getTenantId(principal);

        // Log batch operation start
        auditService.logTransaction(tenantId, "BATCH-" + System.currentTimeMillis(),
                MessageType.V2_TO_FHIR_SYNC, TransactionStatus.ACCEPTED);

        BatchConversionResponse response = batchConversionService.convertHl7ToFhirBatch(request.getMessages());

        // Log batch operation completion
        auditService.logTransaction(tenantId, "BATCH-" + System.currentTimeMillis(),
                MessageType.V2_TO_FHIR_SYNC,
                response.getFailureCount() == 0 ? TransactionStatus.COMPLETED : TransactionStatus.FAILED);

        return ResponseEntity.ok(response);
    }

    /**
     * Batch convert multiple FHIR bundles to HL7 in parallel.
     * 
     * @param fhirBundles List of FHIR Bundle JSON strings
     * @param principal   Authenticated user
     * @return BatchConversionResponse with results and errors
     */
    @PostMapping(value = "/fhir-to-v2-batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchConversionResponse> convertFhirToHl7Batch(
            @RequestBody java.util.List<String> fhirBundles,
            Principal principal) {

        String tenantId = getTenantId(principal);

        // Log batch operation start
        auditService.logTransaction(tenantId, "BATCH-" + System.currentTimeMillis(),
                MessageType.FHIR_TO_V2_SYNC, TransactionStatus.ACCEPTED);

        BatchConversionResponse response = batchConversionService.convertFhirToHl7Batch(fhirBundles);

        // Log batch operation completion
        auditService.logTransaction(tenantId, "BATCH-" + System.currentTimeMillis(),
                MessageType.FHIR_TO_V2_SYNC,
                response.getFailureCount() == 0 ? TransactionStatus.COMPLETED : TransactionStatus.FAILED);

        return ResponseEntity.ok(response);
    }

    /**
     * Handle FHIR validation exceptions with detailed field-level errors
     */
    @ExceptionHandler(FhirValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFhirValidationException(FhirValidationException e) {
        log.error("FHIR validation failed: {}", e.getMessage(), e);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "Validation Error");
        errorResponse.put("message", e.getMessage());
        errorResponse.put("validationErrors", e.getValidationErrors());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception in ConverterController: {}", e.getMessage(), e);
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "Error");
        errorResponse.put("message", e.getMessage());
        errorResponse.put("type", e.getClass().getSimpleName());

        if (e instanceof HL7Exception || e instanceof EncodingNotSupportedException || e instanceof DataFormatException
                || e instanceof JsonParseException || e instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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
