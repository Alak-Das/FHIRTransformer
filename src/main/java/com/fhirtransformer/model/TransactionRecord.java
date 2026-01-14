package com.fhirtransformer.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "transaction_logs")
@CompoundIndex(def = "{'tenantId': 1, 'timestamp': -1}", name = "tenant_timestamp_idx")
public class TransactionRecord {
    @Id
    private String id;

    // Explicitly not unique per tenant, but unique per message technically?
    // Wait, transactionId used for lookup implies uniqueness. I'll make it unique.
    @Indexed(unique = true)
    private String transactionId;

    private String tenantId;
    private String messageType; // e.g., "V2_TO_FHIR", "FHIR_TO_V2"
    private LocalDateTime timestamp;
    private String status; // "ACCEPTED", "COMPLETED", "FAILED"
}
