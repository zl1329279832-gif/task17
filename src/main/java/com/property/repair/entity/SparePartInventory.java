package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Per-location inventory of a spare part.
 * communityId + buildingId scope the warehouse location.
 */
@Data
@TableName("spare_part_inventory")
public class SparePartInventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long partId;

    private Long communityId;

    /** Nullable — null means community-level warehouse */
    private Long buildingId;

    /** Current available quantity (not reserved) */
    private Integer availableQty;

    /** Quantity reserved by pending/approved part requests */
    private Integer reservedQty;

    /** Total = available + reserved */
    private Integer totalQty;

    /** Physical location description (e.g. "B1-A03") */
    private String locationCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
