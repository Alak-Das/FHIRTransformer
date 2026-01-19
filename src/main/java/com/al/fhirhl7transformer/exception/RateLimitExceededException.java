package com.al.fhirhl7transformer.exception;

import lombok.Getter;

/**
 * Exception thrown when a tenant exceeds their rate limit
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final int limit;
    private final int current;
    private final long retryAfterSeconds;

    public RateLimitExceededException(int limit, int current, long retryAfterSeconds) {
        super(String.format("Rate limit exceeded. Limit: %d requests/minute, Current: %d", limit, current));
        this.limit = limit;
        this.current = current;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
