package com.al.fhirhl7transformer.service;

import com.al.fhirhl7transformer.dto.TenantOnboardRequest;
import com.al.fhirhl7transformer.model.Tenant;
import com.al.fhirhl7transformer.repository.TenantRepository;
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
        TenantOnboardRequest request = new TenantOnboardRequest();
        request.setTenantId("tenant1");
        request.setPassword("password123");
        request.setName("Test Hospital");
        request.setRequestLimitPerMinute(60);

        when(tenantRepository.findByTenantId(request.getTenantId())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.onboardTenant(request);

        assertNotNull(result);
        assertEquals(request.getTenantId(), result.getTenantId());
        assertEquals("encodedPassword", result.getPassword());
        assertEquals(request.getName(), result.getName());
        assertEquals(request.getRequestLimitPerMinute(), result.getRequestLimitPerMinute());

        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    public void testOnboardTenant_AlreadyExists() {
        TenantOnboardRequest request = new TenantOnboardRequest();
        request.setTenantId("tenant1");
        request.setPassword("password");
        request.setName("Test Tenant");
        request.setRequestLimitPerMinute(60);

        when(tenantRepository.findByTenantId(request.getTenantId())).thenReturn(Optional.of(new Tenant()));

        assertThrows(IllegalArgumentException.class, () -> {
            tenantService.onboardTenant(request);
        });

        verify(tenantRepository, never()).save(any(Tenant.class));
    }
}
