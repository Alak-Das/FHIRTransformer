package com.fhirtransformer.controller;

import com.fhirtransformer.dto.TransactionSummaryResponse;
import com.fhirtransformer.dto.TransactionDTO;
import com.fhirtransformer.dto.TenantOnboardRequest;
import com.fhirtransformer.dto.TenantUpdateRequest;
import java.util.stream.Collectors;
import java.util.Map;
import com.fhirtransformer.model.Tenant;
import com.fhirtransformer.model.TransactionRecord;
import com.fhirtransformer.repository.TransactionRepository;
import com.fhirtransformer.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final TransactionRepository transactionRepository;

    @Autowired
    public TenantController(TenantService tenantService, TransactionRepository transactionRepository) {
        this.tenantService = tenantService;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/{tenantId}/transactions")
    public ResponseEntity<TransactionSummaryResponse> getTenantTransactions(
            @PathVariable String tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        List<TransactionRecord> records = transactionRepository.findByTenantIdAndTimestampBetween(tenantId, startDate,
                endDate);

        Map<String, Long> statusCounts = records.stream()
                .collect(Collectors.groupingBy(TransactionRecord::getStatus, Collectors.counting()));

        List<TransactionDTO> dtos = records.stream()
                .map(r -> TransactionDTO.builder()
                        .id(r.getId()) // Internal ID
                        .originalMessageId(r.getTransactionId()) // MSH-10 or Bundle.id
                        .messageType(r.getMessageType())
                        .status(r.getStatus())
                        .timestamp(r.getTimestamp())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(TransactionSummaryResponse.builder()
                .totalCount(records.size())
                .statusCounts(statusCounts)
                .transactions(dtos)
                .build());
    }

    @PostMapping("/onboard")
    public ResponseEntity<Tenant> onboardTenant(@Valid @RequestBody TenantOnboardRequest request) {
        Tenant tenant = tenantService.onboardTenant(request.getTenantId(), request.getPassword(), request.getName());
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<Tenant> updateTenant(@PathVariable String tenantId,
            @Valid @RequestBody TenantUpdateRequest request) {
        Tenant tenant = tenantService.updateTenant(tenantId, request.getPassword(), request.getName());
        return ResponseEntity.ok(tenant);
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<String> deleteTenant(@PathVariable String tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.ok("Tenant deleted successfully");
    }
}
