package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Core repair order entity.
 */
@Data
@TableName("repair_order")
public class RepairOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String title;

    private String description;

    private String problemType;

    /** 1=Low, 2=Normal, 3=High, 4=Urgent */
    private Integer urgency;

    // ---- Location ----
    private Long communityId;

    private Long buildingId;

    private Long unitId;

    private String roomNo;

    private String addressDetail;

    // ---- Parties ----
    private Long ownerId;

    private Long assignedWorkerId;

    private Long originalWorkerId;

    // ---- State machine ----
    private String status;

    private String previousStatus;

    private String suspendReason;

    /** ID of the currently active dispatch record (dispatch round tracker) */
    private Long currentDispatchId;

    /** When the order was suspended (null when not suspended) */
    private LocalDateTime suspendedAt;

    /** When the order entered WAITING_PARTS status */
    private LocalDateTime waitingPartsAt;

    /** Cumulative seconds the order has been suspended */
    private Integer totalSuspendedSeconds;

    /** Cumulative seconds the order has been waiting for parts */
    private Integer totalWaitingPartsSeconds;

    // ---- Timestamps ----
    private LocalDateTime submittedAt;

    private LocalDateTime assignedAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime visitAt;

    private LocalDateTime completedAt;

    // ---- Duplicate handling ----
    private Long parentOrderId;

    private Integer isDuplicate;

    private String duplicateNote;

    // ---- Soft delete ----
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
