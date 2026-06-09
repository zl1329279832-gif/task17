package com.property.repair.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Create a purchase request for restocking.
 */
@Data
public class PurchaseRequestCreateRequest {

    @NotNull
    private Long partId;

    private Long communityId;

    private Long buildingId;

    @NotNull
    @Min(1)
    private Integer quantity;

    /** 1=Low, 2=Normal, 3=High, 4=Urgent */
    private Integer urgency = 2;

    private String remark;
}
