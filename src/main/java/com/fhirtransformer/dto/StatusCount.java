package com.fhirtransformer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatusCount {
    private String _id; // Corresponds to 'status' in group by
    private long count;
}
