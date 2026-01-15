package com.fhirtransformer.listener;

import com.fhirtransformer.service.Hl7ToFhirService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class Hl7MessageListener {

    private static final Logger log = LoggerFactory.getLogger(Hl7MessageListener.class);
    private final Hl7ToFhirService hl7ToFhirService;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    // private final TransactionRepository transactionRepository; // Removed
    private final com.fhirtransformer.service.AuditService auditService; // Added

    @org.springframework.beans.factory.annotation.Value("${app.rabbitmq.output-queue}")
    private String outputQueue;

    public Hl7MessageListener(Hl7ToFhirService hl7ToFhirService,
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
            com.fhirtransformer.service.AuditService auditService) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.rabbitTemplate = rabbitTemplate;
        this.auditService = auditService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void receiveMessage(String hl7Message) {
        try {
            log.info("Received HL7 Message: {}", hl7Message);
            String fhirBundle = hl7ToFhirService.convertHl7ToFhir(hl7Message);

            // Publish to Output Queue
            rabbitTemplate.convertAndSend(outputQueue, fhirBundle);
            log.info("Successfully converted and published to {}: {}", outputQueue, fhirBundle);

            // Update Transaction Status
            String[] segments = hl7Message.split("\r");
            String[] mshFields = segments[0].split("\\|", -1);
            if (mshFields.length > 9) {
                String transactionId = mshFields[9];
                auditService.updateTransactionStatus(transactionId, "PROCESSED");
            }

        } catch (Exception e) {
            log.error("Error processing HL7 Message: {}", e.getMessage(), e);
        }
    }
}
