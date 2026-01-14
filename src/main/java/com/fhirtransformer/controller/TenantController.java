package com.fhirtransformer.controller;

import com.fhirtransformer.model.Tenant;
import com.fhirtransformer.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    @Autowired
    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/onboard")
    public ResponseEntity<?> onboardTenant(@RequestBody Map<String, String> request) {
        try {
            String tenantId = request.get("tenantId");
            String password = request.get("password");
            String name = request.get("name");

            if (tenantId == null || password == null) {
                return ResponseEntity.badRequest().body("Missing tenantId or password");
            }

            Tenant tenant = tenantService.onboardTenant(tenantId, password, name);
            return ResponseEntity.ok(tenant);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<?> updateTenant(@PathVariable String tenantId, @RequestBody Map<String, String> request) {
        try {
            String password = request.get("password");
            String name = request.get("name");

            Tenant tenant = tenantService.updateTenant(tenantId, password, name);
            return ResponseEntity.ok(tenant);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<?> deleteTenant(@PathVariable String tenantId) {
        try {
            tenantService.deleteTenant(tenantId);
            return ResponseEntity.ok("Tenant deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
