package com.al.fhirhl7transformer.repository;

import com.al.fhirhl7transformer.model.SubscriptionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends MongoRepository<SubscriptionEntity, String> {
    List<SubscriptionEntity> findByTenantIdAndStatus(String tenantId, String status);
}
