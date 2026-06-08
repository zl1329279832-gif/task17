package com.property.repair.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class DispatchRequest {

    @NotNull(message = "工单ID不能为空")
    private Long orderId;

    @NotNull(message = "维修人员ID不能为空")
    private Long workerId;

    private String reason;
}
