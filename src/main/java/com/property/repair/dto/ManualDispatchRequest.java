package com.property.repair.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualDispatchRequest {

    @NotNull(message = "Worker ID is required")
    private Long workerId;

    private String reason;
}
