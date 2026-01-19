package com.al.fhirhl7transformer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryResponse {
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private Map<String, Long> statusCounts;
    private List<TransactionDTO> transactions;
}
