package com.fhirtransformer.dto;

import lombok.Data;

@Data
public class TenantUpdateRequest {
    private String password;
    private String name;
}
