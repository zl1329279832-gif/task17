package com.property.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CompleteRequest {

    @NotBlank(message = "Completion remark is required")
    private String remark;

    /** Evidence attachment IDs */
    private List<Long> evidenceAttachmentIds;
}
