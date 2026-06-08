package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_progress")
public class RepairProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String fromStatus;

    private String toStatus;

    private Long operatorId;

    private String operatorRole;

    private String remark;

    /** JSON array of evidence photo/video URLs */
    private String evidenceUrls;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
