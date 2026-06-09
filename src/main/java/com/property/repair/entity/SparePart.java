package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spare_part")
public class SparePart {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String partNo;

    private String name;

    private String specification;

    private String unit;

    private String problemType;

    /** 1=critical part that can block work orders */
    private Integer isCritical;

    private Integer minStock;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
