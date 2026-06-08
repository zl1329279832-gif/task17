package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("worker_skill")
public class WorkerSkill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    private Long categoryId;

    private Long communityId;

    @TableField("proficiency")
    private Integer proficiency = 1;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
