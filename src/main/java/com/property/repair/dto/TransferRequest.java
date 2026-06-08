package com.property.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferRequest {

    @NotNull(message = "Target worker ID is required")
    private Long targetWorkerId;

    @NotBlank(message = "Transfer reason is required")
    private String reason;
}
