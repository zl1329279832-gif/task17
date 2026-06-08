package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.property.repair.common.enums.OrderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repair_order")
public class RepairOrder extends BaseEntity {

    private String orderNo;

    private Long ownerId;

    private Long communityId;

    private Long buildingId;

    private String unitNumber;

    private Long categoryId;

    private String title;

    private String description;

    @TableField("urgency")
    private Integer urgency = 1;

    @TableField("status")
    private OrderStatus status;

    private Long currentWorkerId;

    private LocalDateTime expectedTime;

    private LocalDateTime assignedAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime confirmedAt;

    private String duplicateGroup;

    @TableField("rework_count")
    private Integer reworkCount = 0;

    @Version
    @TableField("version")
    private Integer version = 0;
}
