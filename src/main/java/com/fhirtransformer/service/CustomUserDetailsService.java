package com.fhirtransformer.service;

import com.fhirtransformer.model.Tenant;
import com.fhirtransformer.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final TenantRepository tenantRepository;
    private final String adminUsername;
    private final String adminPassword;

    @Autowired
    public CustomUserDetailsService(TenantRepository tenantRepository,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword) {
        this.tenantRepository = tenantRepository;
        // Trim injected values to avoid subtle whitespace issues
        this.adminUsername = adminUsername != null ? adminUsername.trim() : null;
        this.adminPassword = adminPassword != null ? adminPassword.trim() : null;

        log.info("DEBUG: CustomUserDetailsService initialized with admin: '{}', password length: {}",
                this.adminUsername, (this.adminPassword != null ? this.adminPassword.length() : "null"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("DEBUG: loadUserByUsername called for: '{}'", username);

        // First check for admin
        if (adminUsername != null && adminUsername.equals(username)) {
            log.info("DEBUG: User match ADMIN logic.");
            // Encode admin password on the fly to match BCrypt encoder expectations
            // Note: In real app, store encoded password. Here we fix dynamic encode.
            String encodedAdminPassword = new BCryptPasswordEncoder().encode(adminPassword);
            log.info("DEBUG: Encoded admin password generated.");

            return User.builder()
                    .username(adminUsername)
                    .password(encodedAdminPassword)
                    .roles("ADMIN")
                    .build();
        }

        // Check for Tenant
        log.info("DEBUG: checking database for tenant: {}", username);
        Tenant tenant = tenantRepository.findByTenantId(username)
                .orElseThrow(() -> {
                    log.warn("DEBUG: User not found in DB: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        log.info("DEBUG: Found tenant in DB. Password length: {}",
                (tenant.getPassword() != null ? tenant.getPassword().length() : "null"));
        return User.builder()
                .username(tenant.getTenantId())
                .password(tenant.getPassword()) // Stored as encoded in DB
                .roles("TENANT")
                .build();
    }
}
