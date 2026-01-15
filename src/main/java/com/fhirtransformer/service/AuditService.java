package com.fhirtransformer.service;

import com.fhirtransformer.model.TransactionRecord;
import com.fhirtransformer.model.enums.MessageType;
import com.fhirtransformer.model.enums.TransactionStatus;
import com.fhirtransformer.repository.TransactionRepository;
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
        try {
            TransactionRecord record = new TransactionRecord();
            record.setTenantId(tenantId);
            record.setTransactionId(transactionId);
            record.setMessageType(type.name());
            record.setStatus(status.name());
            record.setTimestamp(LocalDateTime.now());
            transactionRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save transaction log for ID {}: {}", transactionId, e.getMessage(), e);
        }
    }
}
