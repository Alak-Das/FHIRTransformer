package com.al.fhirhl7transformer.controller;

import com.al.fhirhl7transformer.model.SubscriptionEntity;
import com.al.fhirhl7transformer.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionEntity> createSubscription(
            @RequestParam String criteria,
            @RequestParam String endpoint,
            @RequestHeader(value = "tenantId", required = false) String tenantId) {

        // Use provided tenantId or default (in real app, get from
        // Principal/SecurityContext)
        String activeTenantId = (tenantId != null) ? tenantId : "default";
        SubscriptionEntity subscription = subscriptionService.createSubscription(activeTenantId, criteria, endpoint);
        return ResponseEntity.ok(subscription);
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionEntity>> getSubscriptions(
            @RequestHeader(value = "tenantId", required = false) String tenantId) {

        String activeTenantId = (tenantId != null) ? tenantId : "default";
        List<SubscriptionEntity> subscriptions = subscriptionService.getActiveSubscriptions(activeTenantId);
        return ResponseEntity.ok(subscriptions);
    }
}
