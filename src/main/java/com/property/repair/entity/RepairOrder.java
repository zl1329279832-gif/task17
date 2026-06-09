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

    /** Incremented on each dispatch/reassignment */
    private Integer dispatchRound;

    /** Incremented on each rework */
    private Integer repairRound;

    private LocalDateTime suspendedAt;

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
