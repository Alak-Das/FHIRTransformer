package com.al.fhirhl7transformer.service;

import com.al.fhirhl7transformer.model.SubscriptionEntity;
import com.al.fhirhl7transformer.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final WebhookService webhookService;

    public SubscriptionEntity createSubscription(String tenantId, String criteria, String endpoint) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setTenantId(tenantId);
        entity.setCriteria(criteria);
        entity.setEndpoint(endpoint);
        entity.setStatus("active");
        entity.setChannelType("rest-hook");
        entity.setCreatedDate(LocalDateTime.now());
        entity.setLastUpdatedDate(LocalDateTime.now());
        return subscriptionRepository.save(entity);
    }

    public List<SubscriptionEntity> getActiveSubscriptions(String tenantId) {
        return subscriptionRepository.findByTenantIdAndStatus(tenantId, "active");
    }

    /**
     * Check if any resources in the Bundle match active subscriptions and notify.
     * Uses parallel processing for improved performance with multiple
     * subscriptions.
     */
    @org.springframework.scheduling.annotation.Async
    public void checkAndNotify(Bundle bundle, String tenantId) {
        List<SubscriptionEntity> subscriptions = getActiveSubscriptions(tenantId);
        if (subscriptions.isEmpty())
            return;

        List<Resource> resources = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .collect(Collectors.toList());

        // Process subscriptions in parallel for better performance
        subscriptions.parallelStream().forEach(sub -> {
            try {
                if (matchesCriteria(sub.getCriteria(), resources)) {
                    log.info("Subscription {} matched for tenant {}. Sending notification to {}",
                            sub.getId(), tenantId, sub.getEndpoint());

                    // Send notification via webhook
                    Map<String, Object> details = Map.of(
                            "subscriptionId", sub.getId(),
                            "criteria", sub.getCriteria(),
                            "event", "resource_created");

                    webhookService.sendNotification(sub.getEndpoint(), "SUB-" + System.currentTimeMillis(),
                            "MATCHED", "FHIR-SUBSCRIPTION", details);
                }
            } catch (Exception e) {
                log.error("Error processing subscription {}: {}", sub.getId(), e.getMessage());
            }
        });
    }

    private boolean matchesCriteria(String criteria, List<Resource> resources) {
        if (criteria == null || criteria.isEmpty())
            return false;

        // Simple matching: ResourceType
        // Criteria format: "ResourceType" or "ResourceType?param=value"
        String resourceType = criteria.split("\\?")[0];

        // Check if any resource matches the type
        return resources.stream()
                .anyMatch(r -> r.getResourceType().name().equalsIgnoreCase(resourceType));

        // TODO: Implement parameter matching (e.g. gender=male) in future iterations
    }
}
