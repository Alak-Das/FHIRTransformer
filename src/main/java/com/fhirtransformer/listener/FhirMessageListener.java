package com.fhirtransformer.listener;

import com.fhirtransformer.service.FhirToHl7Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.fhirtransformer.repository.TransactionRepository;
import com.fhirtransformer.model.TransactionRecord;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@Slf4j
public class FhirMessageListener {

    private final FhirToHl7Service fhirToHl7Service;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionRepository transactionRepository;

    @Value("${app.rabbitmq.v2.output-queue}")
    private String v2OutputQueue;

    @Autowired
    public FhirMessageListener(FhirToHl7Service fhirToHl7Service, RabbitTemplate rabbitTemplate,
            TransactionRepository transactionRepository) {
        this.fhirToHl7Service = fhirToHl7Service;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionRepository = transactionRepository;
    }

    @RabbitListener(queues = "${app.rabbitmq.fhir.queue}")
    public void receiveMessage(String fhirJson) {
        try {
            log.info("Received FHIR Message: {}", fhirJson);
            String hl7Message = fhirToHl7Service.convertFhirToHl7(fhirJson);

            // Publish to Output Queue
            rabbitTemplate.convertAndSend(v2OutputQueue, hl7Message);
            log.info("Successfully converted and published to {}: {}", v2OutputQueue, hl7Message);

            // Update Transaction Status
            String[] segments = hl7Message.split("\r");
            String[] mshFields = segments[0].split("\\|", -1);
            if (mshFields.length > 9) {
                String transactionId = mshFields[9];
                Optional<TransactionRecord> recordOpt = transactionRepository.findByTransactionId(transactionId);
                if (recordOpt.isPresent()) {
                    TransactionRecord record = recordOpt.get();
                    record.setStatus("PROCESSED");
                    transactionRepository.save(record);
                } else {
                    log.warn("Transaction ID {} not found for update", transactionId);
                }
            }

        } catch (Exception e) {
            log.error("Error processing FHIR Message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process FHIR message", e); // Triggers DLQ
        }
    }
}
