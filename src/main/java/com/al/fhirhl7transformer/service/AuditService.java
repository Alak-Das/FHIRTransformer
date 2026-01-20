package com.al.fhirhl7transformer.service;

import com.al.fhirhl7transformer.model.TransactionRecord;
import com.al.fhirhl7transformer.model.enums.MessageType;
import com.al.fhirhl7transformer.model.enums.TransactionStatus;
import com.al.fhirhl7transformer.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final TransactionRepository transactionRepository;

    public AuditService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Async
    public void logTransaction(String tenantId, String transactionId, MessageType type, TransactionStatus status) {
        logTransaction(tenantId, transactionId, type, status, null);
    }

    @Async
    public void logTransaction(String tenantId, String transactionId, MessageType type, TransactionStatus status,
            String idempotencyKey) {
        try {
            TransactionRecord record = new TransactionRecord();
            record.setTenantId(tenantId);
            record.setTransactionId(transactionId);
            record.setMessageType(type.name());
            record.setStatus(status.name());
            record.setTimestamp(LocalDateTime.now());
            record.setIdempotencyKey(idempotencyKey); // Set idempotency key if provided
            transactionRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save transaction log for ID {}: {}", transactionId, e.getMessage(), e);
        }
    }

    @Async
    public void updateTransactionStatus(String transactionId, String status) {
        try {
            transactionRepository.findByTransactionId(transactionId).ifPresent(record -> {
                record.setStatus(status);
                transactionRepository.save(record);
            });
        } catch (Exception e) {
            log.error("Failed to update status for transaction ID {}: {}", transactionId, e.getMessage(), e);
        }
    }

    @Async
    public void updateTransactionFailure(String transactionId, String status, String errorMessage, int retryCount) {
        try {
            transactionRepository.findByTransactionId(transactionId).ifPresent(record -> {
                record.setStatus(status);
                record.setLastErrorMessage(errorMessage);
                record.setRetryCount(retryCount);
                record.setLastRetryAt(LocalDateTime.now());
                transactionRepository.save(record);
            });
        } catch (Exception e) {
            log.error("Failed to update failure details for transaction ID {}: {}", transactionId, e.getMessage(), e);
        }
    }

    @Async
    public void updateTransactionSuccess(String transactionId, String status) {
        try {
            transactionRepository.findByTransactionId(transactionId).ifPresent(record -> {
                record.setStatus(status);
                record.setProcessingCompletedAt(LocalDateTime.now());
                transactionRepository.save(record);
            });
        } catch (Exception e) {
            log.error("Failed to update success details for transaction ID {}: {}", transactionId, e.getMessage(), e);
        }
    }
}
