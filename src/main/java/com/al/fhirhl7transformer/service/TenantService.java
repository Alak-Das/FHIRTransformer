package com.al.fhirhl7transformer.service;

import com.al.fhirhl7transformer.exception.TenantNotFoundException;
import com.al.fhirhl7transformer.dto.TenantOnboardRequest;
import com.al.fhirhl7transformer.model.Tenant;
import com.al.fhirhl7transformer.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public TenantService(TenantRepository tenantRepository, PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Get all tenants.
     * Cached for 1 hour to reduce database load.
     */
    @Cacheable(value = "allTenants")
    public List<Tenant> getAllTenants() {
        log.debug("Fetching all tenants");
        return tenantRepository.findAll();
    }

    /**
     * Onboard a new tenant.
     * Evicts allTenants cache since the list has changed.
     */
    @CacheEvict(value = "allTenants", allEntries = true)
    public Tenant onboardTenant(TenantOnboardRequest request) {
        log.info("Attempting to onboard tenant: {}", request.getTenantId());

        // Check if tenant already exists
        if (tenantRepository.findByTenantId(request.getTenantId()).isPresent()) {
            throw new IllegalArgumentException("Tenant with ID '" + request.getTenantId() + "' already exists");
        }

        Tenant tenant = new Tenant();
        tenant.setTenantId(request.getTenantId());
        tenant.setName(request.getName() != null ? request.getName() : request.getTenantId());
        tenant.setPassword(passwordEncoder.encode(request.getPassword()));

        // Set rate limit (use provided value or default to 60)
        if (request.getRequestLimitPerMinute() != null && request.getRequestLimitPerMinute() > 0) {
            tenant.setRequestLimitPerMinute(request.getRequestLimitPerMinute());
        } else {
            tenant.setRequestLimitPerMinute(60); // Default
        }

        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Successfully onboarded tenant: {} with rate limit: {}", savedTenant.getTenantId(),
                savedTenant.getRequestLimitPerMinute());
        return savedTenant;
    }

    /**
     * Update tenant information.
     * Evicts allTenants cache since tenant data has changed.
     */
    @CacheEvict(value = "allTenants", allEntries = true)
    public Tenant updateTenant(String tenantId, String password, String name) {
        log.info("Updating tenant: {}", tenantId);
        return tenantRepository.findByTenantId(tenantId)
                .map(tenant -> {
                    if (password != null && !password.isEmpty()) {
                        tenant.setPassword(passwordEncoder.encode(password));
                    }
                    if (name != null) {
                        tenant.setName(name);
                    }
                    Tenant updated = tenantRepository.save(tenant);
                    log.info("Successfully updated tenant: {}", tenantId);
                    return updated;
                })
                .orElseThrow(() -> {
                    log.error("Failed to update: Tenant not found: {}", tenantId);
                    return new TenantNotFoundException("Tenant with ID " + tenantId + " not found");
                });
    }

    /**
     * Delete a tenant.
     * Evicts allTenants cache since the list has changed.
     */
    @CacheEvict(value = "allTenants", allEntries = true)
    public void deleteTenant(String tenantId) {
        log.info("Attempting to delete tenant: {}", tenantId);
        Optional<Tenant> tenant = tenantRepository.findByTenantId(tenantId);
        if (tenant.isPresent()) {
            tenantRepository.delete(tenant.get());
            log.info("Successfully deleted tenant: {}", tenantId);
        } else {
            log.error("Failed to delete: Tenant not found: {}", tenantId);
            throw new TenantNotFoundException("Tenant with ID " + tenantId + " not found");
        }
    }
}
