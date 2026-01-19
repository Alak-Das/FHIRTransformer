package com.al.fhirhl7transformer.repository;

import com.al.fhirhl7transformer.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends MongoRepository<Tenant, String> {
    Optional<Tenant> findByTenantId(String tenantId);

    void deleteByTenantId(String tenantId);
}
