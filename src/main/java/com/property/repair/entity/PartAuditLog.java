package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Audit log for spare part operations — tracks every inventory change,
 * request approval, issuance, return, and purchase action.
 */
@Data
@TableName("part_audit_log")
public class PartAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SPARE_PART, INVENTORY, PART_REQUEST, PURCHASE_REQUEST */
    private String entityType;

    private Long entityId;

    /** Linked repair order (if applicable) */
    private Long orderId;

    /** Action performed: CREATE, APPROVE, ISSUE, RETURN, CONSUME, PURCHASE, STOCK_IN, STOCK_ADJUST */
    private String action;

    private Long userId;

    private String userRole;

    /** JSON snapshot before action */
    private String beforeData;

    /** JSON snapshot after action */
    private String afterData;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
