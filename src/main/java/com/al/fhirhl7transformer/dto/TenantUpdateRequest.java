package com.al.fhirhl7transformer.dto;

import lombok.Data;

@Data
public class TenantUpdateRequest {
    private String password;
    private String name;
}
