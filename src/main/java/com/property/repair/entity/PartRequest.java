package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Spare part request — worker requests parts for a repair order.
 */
@Data
@TableName("part_request")
public class PartRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long workerId;

    /** Unique request number, e.g. "PR20260609000001" */
    private String requestNo;

    /** NORMAL or REWORK */
    private String requestType;

    /** PENDING, APPROVED, PARTIALLY_ISSUED, ISSUED, RETURNED, CANCELLED */
    private String status;

    /** Remark from the worker */
    private String remark;

    /** Approver user ID */
    private Long approvedBy;

    private LocalDateTime approvedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
