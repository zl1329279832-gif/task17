package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rework_order")
public class ReworkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String reworkNo;

    private String reason;

    private String description;

    private Long assignedWorkerId;

    /** PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED */
    private String status;

    private Long requesterId;

    private LocalDateTime completedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
