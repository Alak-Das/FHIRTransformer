package com.fhirtransformer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhirtransformer.model.Tenant;
import com.fhirtransformer.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc

public class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    public void testOnboardTenant_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("tenantId", "tenant1");
        request.put("password", "password123");
        request.put("name", "Test Hospital");

        Tenant tenant = new Tenant();
        tenant.setTenantId("tenant1");
        tenant.setName("Test Hospital");

        when(tenantService.onboardTenant(anyString(), anyString(), anyString())).thenReturn(tenant);

        mockMvc.perform(post("/api/tenants/onboard")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant1"))
                .andExpect(jsonPath("$.name").value("Test Hospital"));
    }

    @Test
    public void testOnboardTenant_Unauthorized() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("tenantId", "tenant1");
        request.put("password", "password123");
        request.put("name", "Test Hospital");

        mockMvc.perform(post("/api/tenants/onboard")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @org.springframework.context.annotation.Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
