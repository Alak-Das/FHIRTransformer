package com.al.fhirhl7transformer.service;

import com.al.fhirhl7transformer.model.TransactionRecord;
import com.al.fhirhl7transformer.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class IdempotencyService {

    private final TransactionRepository transactionRepository;

    @Autowired
    public IdempotencyService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Check if a request with the given idempotency key has already been processed.
     *
     * @param idempotencyKey The client-provided idempotency key
     * @return Optional containing the existing transaction record if found
     */
    public Optional<TransactionRecord> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Optional<TransactionRecord> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            log.info("Found existing transaction for idempotency key: {}", idempotencyKey);
        }

        return existing;
    }

    /**
     * Validate idempotency key format.
     *
     * @param idempotencyKey The key to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }

        // RFC 7231: Idempotency key should be ASCII, max 255 characters
        if (idempotencyKey.length() > 255) {
            log.warn("Idempotency key exceeds 255 characters: {}", idempotencyKey.length());
            return false;
        }

        return true;
    }
}
