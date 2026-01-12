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

    @Value("${app.rabbitmq.v2.output-queue}")
    private String v2OutputQueue;

    @Autowired
    public FhirMessageListener(FhirToHl7Service fhirToHl7Service, RabbitTemplate rabbitTemplate) {
        this.fhirToHl7Service = fhirToHl7Service;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "${app.rabbitmq.fhir.queue}")
    public void receiveMessage(String fhirJson) {
        try {
            log.info("Received FHIR Message: {}", fhirJson);
            String hl7Message = fhirToHl7Service.convertFhirToHl7(fhirJson);

            // Publish to Output Queue
            rabbitTemplate.convertAndSend(v2OutputQueue, hl7Message);
            log.info("Successfully converted and published to {}: {}", v2OutputQueue, hl7Message);

        } catch (Exception e) {
            log.error("Error processing FHIR Message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process FHIR message", e); // Triggers DLQ
        }
    }
}
