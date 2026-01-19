package com.fhirtransformer.interceptor;

import com.fhirtransformer.exception.RateLimitExceededException;
import com.fhirtransformer.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;

/**
 * Interceptor to enforce per-tenant rate limiting on API requests
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Skip rate limiting for health checks and actuator endpoints
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/actuator/")) {
            return true;
        }

        // Get authenticated user (tenant ID)
        Principal principal = request.getUserPrincipal();
        if (principal == null) {
            // No authentication, skip rate limiting (will be handled by security layer)
            return true;
        }

        String tenantId = principal.getName();

        try {
            // Check and increment rate limit
            rateLimitService.checkRateLimit(tenantId);

            // Add rate limit headers to response
            int limit = rateLimitService.getLimit(tenantId);
            int remaining = limit - rateLimitService.getCurrentCount(tenantId);

            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

            return true;
        } catch (RateLimitExceededException e) {
            // Rate limit exceeded - return 429
            response.setStatus(429); // Too Many Requests
            response.setHeader("X-RateLimit-Limit", String.valueOf(e.getLimit()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                    "{\"error\":\"Too Many Requests\",\"message\":\"%s\",\"retryAfter\":%d}",
                    e.getMessage(), e.getRetryAfterSeconds()));

            return false; // Stop request processing
        }
    }
}
