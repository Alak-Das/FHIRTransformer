package com.fhirtransformer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantOnboardRequest {
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Name is required")
    private String name;
}
