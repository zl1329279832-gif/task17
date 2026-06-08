package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_building")
public class Building {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long communityId;

    private String name;

    private Integer units;

    private Integer floors;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
