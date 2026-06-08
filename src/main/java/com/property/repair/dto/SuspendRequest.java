package com.property.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuspendRequest {

    @NotBlank(message = "Suspend reason is required")
    private String reason;
}
