package com.al.fhirhl7transformer.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

@Data
@Document(collection = "subscriptions")
public class SubscriptionEntity {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String criteria; // e.g. "Patient?gender=male"
    private String status; // active, off, error
    private String channelType; // rest-hook, websocket
    private String endpoint; // Webhook URL
    private String payloadMimeType; // application/fhir+json

    private LocalDateTime createdDate;
    private LocalDateTime lastUpdatedDate;
}
