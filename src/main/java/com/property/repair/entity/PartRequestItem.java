package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Line item in a spare part request.
 */
@Data
@TableName("part_request_item")
public class PartRequestItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requestId;

    private Long partId;

    /** Requested quantity */
    private Integer requestedQty;

    /** Actually issued quantity */
    private Integer issuedQty;

    /** Returned quantity (unused parts) */
    private Integer returnedQty;

    /** Consumed quantity (used in repair, won't be returned) */
    private Integer consumedQty;

    /** PENDING, APPROVED, ISSUED, PARTIALLY_ISSUED, RETURNED */
    private String status;

    /** Whether this part is critical — if insufficient, order enters WAITING_PARTS */
    private Integer critical;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
