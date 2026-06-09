package com.property.repair.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Spare part inventory detail response.
 */
@Data
public class SparePartInventoryVO {

    private Long id;
    private Long partId;
    private String partCode;
    private String partName;
    private String category;
    private String specification;
    private String unit;
    private Long communityId;
    private String communityName;
    private Long buildingId;
    private String buildingName;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer totalQty;
    private Integer safetyStock;
    private String locationCode;
    /** Whether stock is below safety level */
    private boolean belowSafetyStock;
    private LocalDateTime updatedAt;
}
