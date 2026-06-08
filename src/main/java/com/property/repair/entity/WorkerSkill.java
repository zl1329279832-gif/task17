package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_worker_skill")
public class WorkerSkill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    private String problemType;

    /** 1-5 proficiency, 5=expert */
    private Integer proficiency;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
