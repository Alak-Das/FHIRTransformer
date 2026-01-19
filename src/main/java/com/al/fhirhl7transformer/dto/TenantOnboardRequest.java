package com.al.fhirhl7transformer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantOnboardRequest {
    @NotBlank(message = "Tenant ID cannot be blank")
    private String tenantId;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    private String name;

    // Optional: Rate limit in requests per minute (default: 60)
    private Integer requestLimitPerMinute = 60;
}
