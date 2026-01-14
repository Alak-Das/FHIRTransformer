package com.fhirtransformer.service;

import com.fhirtransformer.model.Tenant;
import com.fhirtransformer.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TenantService tenantService;

    @Test
    public void testOnboardTenant_Success() {
        String tenantId = "tenant1";
        String password = "password123";
        String name = "Test Hospital";

        when(tenantRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn("encodedPassword");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.onboardTenant(tenantId, password, name);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertEquals("encodedPassword", result.getPassword());
        assertEquals(name, result.getName());

        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    public void testOnboardTenant_AlreadyExists() {
        String tenantId = "tenant1";
        when(tenantRepository.findByTenantId(tenantId)).thenReturn(Optional.of(new Tenant()));

        assertThrows(RuntimeException.class, () -> {
            tenantService.onboardTenant(tenantId, "password", "name");
        });

        verify(tenantRepository, never()).save(any(Tenant.class));
    }
}
