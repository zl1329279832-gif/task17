package com.property.repair.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
public class ProgressUpdateRequest {

    @NotBlank(message = "备注不能为空")
    private String remark;

    private List<Long> attachmentIds;
}
