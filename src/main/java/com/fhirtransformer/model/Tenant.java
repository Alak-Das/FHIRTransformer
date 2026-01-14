package com.fhirtransformer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "tenants")
public class Tenant {
    @Id
    private String id;
    private String tenantId;
    @JsonIgnore
    private String password;
    private String name;
}
