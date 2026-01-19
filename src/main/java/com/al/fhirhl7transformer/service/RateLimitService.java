package com.al.fhirhl7transformer.service;

import com.al.fhirhl7transformer.exception.RateLimitExceededException;
import com.al.fhirhl7transformer.model.Tenant;
import com.al.fhirhl7transformer.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Service for enforcing per-tenant rate limiting using Redis
 */
@Service
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final TenantRepository tenantRepository;

    public RateLimitService(RedisTemplate<String, Object> redisTemplate, TenantRepository tenantRepository) {
        this.redisTemplate = redisTemplate;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Check if tenant has exceeded their rate limit and increment counter
     * 
     * @param tenantId The tenant ID
     * @throws RateLimitExceededException if limit is exceeded
     */
    public void checkRateLimit(String tenantId) {
        // Get tenant configuration or use defaults
        int limit = getLimit(tenantId);

        // Get current minute timestamp (epoch seconds / 60)
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String key = String.format("rate_limit:%s:%d", tenantId, currentMinute);

        // Increment counter
        Long currentCount = redisTemplate.opsForValue().increment(key);

        // Set expiration on first increment (2 minutes to handle clock drift)
        if (currentCount == 1) {
            redisTemplate.expire(key, 2, TimeUnit.MINUTES);
        }

        log.debug("Rate limit check for tenant {}: {}/{} requests", tenantId, currentCount, limit);

        // Check if limit exceeded
        if (currentCount > limit) {
            // Calculate retry-after (seconds until next minute)
            long retryAfter = 60 - (Instant.now().getEpochSecond() % 60);

            log.warn("Rate limit exceeded for tenant {}: {}/{} requests", tenantId, currentCount, limit);
            throw new RateLimitExceededException(limit, currentCount.intValue(), retryAfter);
        }
    }

    /**
     * Get current request count for a tenant in this minute
     */
    public int getCurrentCount(String tenantId) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String key = String.format("rate_limit:%s:%d", tenantId, currentMinute);

        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    /**
     * Get rate limit for a tenant
     * Returns default limits if tenant not found:
     * - admin: 1000 requests/minute
     * - others: 60 requests/minute
     */
    public int getLimit(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .map(Tenant::getRequestLimitPerMinute)
                .orElseGet(() -> {
                    // Default: High limit for admin, standard for others
                    int defaultLimit = "admin".equals(tenantId) ? 1000 : 60;
                    log.debug("Tenant {} not found, using default rate limit: {}", tenantId, defaultLimit);
                    return defaultLimit;
                });
    }
}
