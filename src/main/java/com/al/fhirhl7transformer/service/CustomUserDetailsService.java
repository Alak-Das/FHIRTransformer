package com.al.fhirhl7transformer.service;

import com.al.fhirhl7transformer.model.Tenant;
import com.al.fhirhl7transformer.repository.TenantRepository;
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
        private final String encodedAdminPassword;

        @Autowired
        public CustomUserDetailsService(TenantRepository tenantRepository,
                        @Value("${app.admin.username}") String adminUsername,
                        @Value("${app.admin.password}") String adminPassword) {
                this.tenantRepository = tenantRepository;
                // Trim injected values to avoid subtle whitespace issues
                this.adminUsername = adminUsername != null ? adminUsername.trim() : null;
                String trimmedPassword = adminPassword != null ? adminPassword.trim() : null;

                // Pre-encode admin password to avoid hashing on every request (DoS prevention)
                if (trimmedPassword != null) {
                        this.encodedAdminPassword = new BCryptPasswordEncoder().encode(trimmedPassword);
                } else {
                        this.encodedAdminPassword = null;
                }

                log.info("DEBUG: CustomUserDetailsService initialized with admin: '{}'", sanitize(this.adminUsername));
        }

        private String sanitize(String input) {
                return input != null ? input.replaceAll("[\\r\\n]", "_") : "null";
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                // Sanitize username for logging
                String safeUsername = sanitize(username);
                log.debug("DEBUG: loadUserByUsername called for: '{}'", safeUsername);

                // First check for admin
                if (adminUsername != null && adminUsername.equals(username)) {
                        log.debug("DEBUG: User match ADMIN logic.");
                        if (encodedAdminPassword == null) {
                                throw new UsernameNotFoundException("Admin password not configured");
                        }

                        return User.builder()
                                        .username(adminUsername)
                                        .password(encodedAdminPassword) // Use pre-encoded password
                                        .roles("ADMIN")
                                        .build();
                }

                // Check for Tenant
                log.debug("DEBUG: checking database for tenant: {}", safeUsername);
                Tenant tenant = tenantRepository.findByTenantId(username)
                                .orElseThrow(() -> {
                                        log.warn("DEBUG: User not found in DB: {}", safeUsername);
                                        return new UsernameNotFoundException("User not found: " + safeUsername);
                                });

                log.debug("DEBUG: Found tenant in DB.");
                return User.builder()
                                .username(tenant.getTenantId())
                                .password(tenant.getPassword()) // Stored as encoded in DB
                                .roles("TENANT")
                                .build();
        }
}
