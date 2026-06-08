package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("timeout_escalation")
public class TimeoutEscalation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** ACCEPT_TIMEOUT, VISIT_TIMEOUT, COMPLETE_TIMEOUT */
    private String timeoutType;

    private LocalDateTime deadline;

    private Long escalatedTo;

    /** 1=first level, 2=second level escalation */
    private Integer escalationLevel;

    /** 0=pending, 1=handled */
    private Integer handled;

    private LocalDateTime handledAt;

    private Long handledBy;

    private String handleRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
