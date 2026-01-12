package com.fhirtransformer.controller;

import com.fhirtransformer.service.Hl7ToFhirService;
import com.fhirtransformer.service.FhirToHl7Service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/convert")
public class ConverterController {

    private final Hl7ToFhirService hl7ToFhirService;
    private final FhirToHl7Service fhirToHl7Service;
    private final RabbitTemplate rabbitTemplate;

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
            RabbitTemplate rabbitTemplate) {
        this.hl7ToFhirService = hl7ToFhirService;
        this.fhirToHl7Service = fhirToHl7Service;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping(value = "/v2-to-fhir", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhir(@RequestBody String hl7Message) {
        // Publish to RabbitMQ for Async Processing
        rabbitTemplate.convertAndSend(exchange, routingKey, hl7Message);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("{\"status\": \"Accepted\", \"message\": \"Processing asynchronously\"}");
    }

    @PostMapping(value = "/v2-to-fhir-sync", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertToFhirSync(@RequestBody String hl7Message) {
        try {
            String fhirJson = hl7ToFhirService.convertHl7ToFhir(hl7Message);
            return ResponseEntity.ok(fhirJson);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/fhir-to-v2", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertToHl7(@RequestBody String fhirJson) {
        // Publish to RabbitMQ for Async Processing
        rabbitTemplate.convertAndSend(fhirExchange, fhirRoutingKey, fhirJson);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Status: Accepted, Message: Processing asynchronously");
    }

    @PostMapping(value = "/fhir-to-v2-sync", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convertToHl7Sync(@RequestBody String fhirJson) {
        try {
            String hl7Message = fhirToHl7Service.convertFhirToHl7(fhirJson);
            return ResponseEntity.ok(hl7Message);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
