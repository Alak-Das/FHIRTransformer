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

    @org.springframework.beans.factory.annotation.Value("${app.rabbitmq.output-queue}")
    private String outputQueue;

    public Hl7MessageListener(Hl7ToFhirService hl7ToFhirService,
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void receiveMessage(String hl7Message) {
        try {
            log.info("Received HL7 Message: {}", hl7Message);
            String fhirBundle = hl7ToFhirService.convertHl7ToFhir(hl7Message);

            // Publish to Output Queue
            rabbitTemplate.convertAndSend(outputQueue, fhirBundle);
            log.info("Successfully converted and published to {}: {}", outputQueue, fhirBundle);

        } catch (Exception e) {
            log.error("Error processing HL7 Message: {}", e.getMessage(), e);
        }
    }
}
