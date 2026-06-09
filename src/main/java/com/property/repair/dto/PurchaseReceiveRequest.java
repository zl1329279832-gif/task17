package com.property.repair.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseReceiveRequest {

    @NotNull
    private Integer receivedQuantity;

    private String remark;
}
