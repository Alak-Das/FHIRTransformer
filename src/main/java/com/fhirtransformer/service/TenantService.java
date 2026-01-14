package com.fhirtransformer.service;

import com.fhirtransformer.exception.TenantAlreadyExistsException;
import com.fhirtransformer.exception.TenantNotFoundException;
import com.fhirtransformer.model.Tenant;
import com.fhirtransformer.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public TenantService(TenantRepository tenantRepository, PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public Tenant onboardTenant(String tenantId, String password, String name) {
        if (tenantRepository.findByTenantId(tenantId).isPresent()) {
            throw new TenantAlreadyExistsException("Tenant with ID " + tenantId + " already exists");
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setPassword(passwordEncoder.encode(password));
        tenant.setName(name);
        return tenantRepository.save(tenant);
    }

    public Tenant updateTenant(String tenantId, String password, String name) {
        return tenantRepository.findByTenantId(tenantId)
                .map(tenant -> {
                    if (password != null && !password.isEmpty()) {
                        tenant.setPassword(passwordEncoder.encode(password));
                    }
                    if (name != null) {
                        tenant.setName(name);
                    }
                    return tenantRepository.save(tenant);
                })
                .orElseThrow(() -> new TenantNotFoundException("Tenant with ID " + tenantId + " not found"));
    }

    public void deleteTenant(String tenantId) {
        Optional<Tenant> tenant = tenantRepository.findByTenantId(tenantId);
        if (tenant.isPresent()) {
            tenantRepository.delete(tenant.get());
        } else {
            throw new TenantNotFoundException("Tenant with ID " + tenantId + " not found");
        }
    }
}
