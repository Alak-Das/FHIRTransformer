package com.al.fhirhl7transformer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
    private String fhirhl7transformerId; // Internal DB ID
    private String originalMessageId; // MSH-10 or Bundle.id
    private String messageType;
    private String status;
    private LocalDateTime timestamp;
}
