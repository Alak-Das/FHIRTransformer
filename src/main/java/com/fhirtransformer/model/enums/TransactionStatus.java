package com.fhirtransformer.model.enums;

public enum TransactionStatus {
    ACCEPTED,
    QUEUED,
    PROCESSED, // Adding this as seen in previous conversations
    COMPLETED,
    FAILED
}
