package com.fhirtransformer.repository;

import com.fhirtransformer.model.TransactionRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends MongoRepository<TransactionRecord, String> {
    List<TransactionRecord> findByTenantIdAndTimestampBetween(String tenantId, LocalDateTime start, LocalDateTime end);
}
