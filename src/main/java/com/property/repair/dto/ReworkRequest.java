package com.property.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ReworkRequest {

    @NotBlank(message = "Rework reason is required")
    private String reason;

    private String description;

    /** Attachment IDs for rework evidence */
    private List<Long> attachmentIds;
}
