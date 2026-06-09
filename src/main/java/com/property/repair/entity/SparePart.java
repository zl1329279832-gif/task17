package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Spare part master data — the catalog of available part types.
 */
@Data
@TableName("spare_part")
public class SparePart {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Unique part code, e.g. "PLUMB-FAUCET-001" */
    private String partCode;

    private String partName;

    /** PLUMBING, ELECTRICAL, CIVIL, FACILITY, OTHER */
    private String category;

    /** Specification/model description */
    private String specification;

    /** Unit of measure: PCS, METER, SET, etc. */
    private String unit;

    /** Minimum stock level that triggers purchase alert */
    private Integer safetyStock;

    /** Unit price for cost tracking */
    private BigDecimal unitPrice;

    /** 1=enabled, 0=disabled */
    private Integer enabled;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
