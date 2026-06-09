package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("spare_part_requisition_item")
public class SparePartRequisitionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requisitionId;

    private Long partId;

    private Integer requestedQuantity;

    private Integer issuedQuantity;

    private Integer consumedQuantity;

    private Integer returnedQuantity;
}
