package com.fhirtransformer.controller;

import com.fhirtransformer.dto.TransactionSummaryResponse;
import com.fhirtransformer.dto.TransactionDTO;
import com.fhirtransformer.dto.TenantOnboardRequest;
import com.fhirtransformer.dto.TenantUpdateRequest;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.fhirtransformer.dto.StatusCount;
import java.util.Map;
import com.fhirtransformer.model.Tenant;
import com.fhirtransformer.model.TransactionRecord;
import com.fhirtransformer.service.TenantService;
import com.fhirtransformer.service.TransactionService;
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
    private final TransactionService transactionService;

    @Autowired
    public TenantController(TenantService tenantService, TransactionService transactionService) {
        this.tenantService = tenantService;
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/{tenantId}/transactions")
    public ResponseEntity<TransactionSummaryResponse> getTenantTransactions(
            @PathVariable String tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransactionRecord> pageRecords = transactionService.findByTenantIdAndTimestampBetween(
                tenantId, startDate, endDate, PageRequest.of(page, size, Sort.by("timestamp").descending()));

        List<StatusCount> statusStats = transactionService.countStatusByTenantIdAndTimestampBetween(
                tenantId, startDate, endDate);

        Map<String, Long> statusCounts = statusStats.stream()
                .collect(Collectors.toMap(StatusCount::get_id, StatusCount::getCount));

        List<TransactionDTO> dtos = pageRecords.getContent().stream()
                .map(r -> TransactionDTO.builder()
                        .fhirTransformerId(r.getId())
                        .originalMessageId(r.getTransactionId())
                        .messageType(r.getMessageType())
                        .status(r.getStatus())
                        .timestamp(r.getTimestamp())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(TransactionSummaryResponse.builder()
                .totalCount(pageRecords.getTotalElements())
                .totalPages(pageRecords.getTotalPages())
                .currentPage(pageRecords.getNumber())
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
