package com.al.fhirhl7transformer.repository;

import com.al.fhirhl7transformer.dto.StatusCount;
import com.al.fhirhl7transformer.model.TransactionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<TransactionRecord, String> {

        @Aggregation(pipeline = {
                        "{ $match: { tenantId: ?0, timestamp: { $gte: ?1, $lte: ?2 } } }",
                        "{ $group: { _id: '$status', count: { $sum: 1 } } }"
        })
        List<StatusCount> countStatusByTenantIdAndTimestampBetween(String tenantId, LocalDateTime start,
                        LocalDateTime end);

        Page<TransactionRecord> findByTenantIdAndTimestampBetween(String tenantId, LocalDateTime start,
                        LocalDateTime end,
                        Pageable pageable);

        Optional<TransactionRecord> findByTransactionId(String transactionId);

        Optional<TransactionRecord> findByIdempotencyKey(String idempotencyKey);
}
