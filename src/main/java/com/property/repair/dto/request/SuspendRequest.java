package com.property.repair.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class SuspendRequest {

    @NotBlank(message = "挂起原因不能为空")
    private String reason;
}
