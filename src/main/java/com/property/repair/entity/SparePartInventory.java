package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spare_part_inventory")
public class SparePartInventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long partId;

    private Long communityId;

    private Integer quantity;

    private Integer reservedQuantity;

    private LocalDateTime updatedAt;
}
