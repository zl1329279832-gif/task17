package com.property.repair.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class ReworkRequest {

    @NotBlank(message = "返工原因不能为空")
    private String reason;
}
