package com.al.fhirhl7transformer.listener;

import com.al.fhirhl7transformer.service.Hl7ToFhirService;
import com.al.fhirhl7transformer.service.WebhookService;
import com.al.fhirhl7transformer.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Hl7MessageListener {

    private static final Logger log = LoggerFactory.getLogger(Hl7MessageListener.class);
    private final Hl7ToFhirService hl7ToFhirService;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    private final AuditService auditService;
    private final WebhookService webhookService;

    @Value("${app.rabbitmq.output-queue}")
    private String outputQueue;

    @Value("${app.webhook.url:#{null}}")
    private String defaultWebhookUrl;

    public Hl7MessageListener(Hl7ToFhirService hl7ToFhirService,
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
            AuditService auditService,
            WebhookService webhookService) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.rabbitTemplate = rabbitTemplate;
        this.auditService = auditService;
        this.webhookService = webhookService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void receiveMessage(
            String hl7Message,
            @org.springframework.messaging.handler.annotation.Header(value = "tenantId", required = false) String tenantId,
            @org.springframework.messaging.handler.annotation.Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount) {
        try {
            if (tenantId != null) {
                com.al.fhirhl7transformer.config.TenantContext.setTenantId(tenantId);
            }
            log.info("Processing HL7 message with TenantID: {} (retry attempt: {})", tenantId, retryCount);

            // Convert
            String fhirBundle = hl7ToFhirService.convertHl7ToFhir(hl7Message);

            // Publish to Output Queue
            rabbitTemplate.convertAndSend(outputQueue, fhirBundle);
            log.info("Successfully converted and published to {}", outputQueue);

            // Update Status and Notify
            String transactionId = extractTransactionId(hl7Message);
            if (transactionId != null) {
                auditService.updateTransactionSuccess(transactionId, "COMPLETED");
                if (defaultWebhookUrl != null) {
                    // Pass 0 for resourceCount as we don't parse the bundle here for perf reasons
                    webhookService.notifyCompletion(defaultWebhookUrl, transactionId, "V2_TO_FHIR", 0);
                }
            }

        } catch (Exception e) {
            log.error("Error processing HL7 Message (attempt {}): {}", retryCount, e.getMessage(), e);

            String transactionId = extractTransactionId(hl7Message);

            if (retryCount < 3) {
                // Route to appropriate retry queue
                int nextRetry = retryCount + 1;
                String retryRoutingKey = "hl7.retry." + nextRetry;

                // Track retry in DB
                if (transactionId != null) {
                    auditService.updateTransactionFailure(transactionId, "RETRYING", e.getMessage(), nextRetry);
                }

                rabbitTemplate.convertAndSend(
                        "hl7-messages-exchange",
                        retryRoutingKey,
                        hl7Message,
                        message -> {
                            message.getMessageProperties().setHeader("x-retry-count", nextRetry);
                            message.getMessageProperties().setHeader("tenantId", tenantId);
                            message.getMessageProperties().setHeader("x-first-failure-reason",
                                    e.getClass().getSimpleName());
                            return message;
                        });

                log.info("Message routed to retry queue '{}' (attempt {} of 3)", retryRoutingKey, nextRetry);
            } else {
                // Max retries exhausted
                if (transactionId != null) {
                    auditService.updateTransactionFailure(transactionId, "FAILED", e.getMessage(), retryCount);
                    if (defaultWebhookUrl != null) {
                        webhookService.notifyFailure(defaultWebhookUrl, transactionId, "V2_TO_FHIR", e.getMessage(),
                                retryCount);
                    }
                }
                log.error("Max retries exhausted for transaction: {}", transactionId);
                throw new RuntimeException("Max retries exceeded after 3 attempts", e);
            }

        } finally {
            com.al.fhirhl7transformer.config.TenantContext.clear();
        }
    }

    /**
     * Extract transaction ID from HL7 message (MSH-10 field)
     */
    private String extractTransactionId(String hl7Message) {
        try {
            String[] segments = hl7Message.split("\r");
            if (segments.length > 0) {
                String[] mshFields = segments[0].split("\\|", -1);
                if (mshFields.length > 9) {
                    return mshFields[9];
                }
            }
        } catch (Exception ex) {
            log.warn("Could not extract transaction ID from message: {}", ex.getMessage());
        }
        return null;
    }
}
