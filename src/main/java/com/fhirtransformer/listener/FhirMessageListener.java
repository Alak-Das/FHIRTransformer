package com.fhirtransformer.listener;

import com.fhirtransformer.service.FhirToHl7Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FhirMessageListener {

    private final FhirToHl7Service fhirToHl7Service;
    private final RabbitTemplate rabbitTemplate;
    // private final TransactionRepository transactionRepository; // Removed
    private final com.fhirtransformer.service.AuditService auditService; // Added

    @Value("${app.rabbitmq.v2.output-queue}")
    private String v2OutputQueue;

    @Autowired
    public FhirMessageListener(FhirToHl7Service fhirToHl7Service, RabbitTemplate rabbitTemplate,
            com.fhirtransformer.service.AuditService auditService) {
        this.fhirToHl7Service = fhirToHl7Service;
        this.rabbitTemplate = rabbitTemplate;
        this.auditService = auditService;
    }

    @RabbitListener(queues = "${app.rabbitmq.fhir.queue}")
    public void receiveMessage(
            String fhirJson,
            @org.springframework.messaging.handler.annotation.Header(value = "x-retry-count", required = false, defaultValue = "0") Integer retryCount) {
        try {
            log.info("Processing FHIR message (retry attempt: {})", retryCount);
            String hl7Message = fhirToHl7Service.convertFhirToHl7(fhirJson);

            // Publish to Output Queue
            rabbitTemplate.convertAndSend(v2OutputQueue, hl7Message);
            log.info("Successfully converted and published to {}", v2OutputQueue);

            // Update Transaction Status to PROCESSED
            String transactionId = extractTransactionId(hl7Message);
            if (transactionId != null) {
                auditService.updateTransactionStatus(transactionId, "PROCESSED");
            }

        } catch (Exception e) {
            log.error("Error processing FHIR Message (attempt {}): {}", retryCount, e.getMessage(), e);

            if (retryCount < 3) {
                // Route to appropriate retry queue
                int nextRetry = retryCount + 1;
                String retryRoutingKey = "fhir.retry." + nextRetry;

                rabbitTemplate.convertAndSend(
                        "fhir-messages-exchange",
                        retryRoutingKey,
                        fhirJson,
                        message -> {
                            message.getMessageProperties().setHeader("x-retry-count", nextRetry);
                            message.getMessageProperties().setHeader("x-first-failure-reason",
                                    e.getClass().getSimpleName());
                            return message;
                        });

                log.info("Message routed to retry queue '{}' (attempt {} of 3)", retryRoutingKey, nextRetry);
            } else {
                // Max retries exhausted
                log.error("Max retries exhausted for FHIR message");
                throw new RuntimeException("Max retries exceeded after 3 attempts", e);
            }
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
            log.warn("Could not extract transaction ID from HL7: {}", ex.getMessage());
        }
        return null;
    }
}
