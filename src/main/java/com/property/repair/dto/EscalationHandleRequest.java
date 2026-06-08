package com.property.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EscalationHandleRequest {

    @NotBlank(message = "Handle remark is required")
    private String remark;

    /** Optional: reassign to a different worker */
    private Long reassignWorkerId;
}
