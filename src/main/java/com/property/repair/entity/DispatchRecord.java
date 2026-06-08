package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dispatch_record")
public class DispatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long workerId;

    /** AUTO, MANUAL, TRANSFER, ESCALATION */
    private String dispatchType;

    private Long fromWorkerId;

    private String reason;

    /** Worker's active order count at dispatch time */
    private Integer loadBefore;

    /** Auto-dispatch match score */
    private BigDecimal score;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
