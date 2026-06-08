package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("building")
public class Building extends BaseEntity {

    private Long communityId;

    private String name;

    private Integer floorCount;

    private BigDecimal longitude;

    private BigDecimal latitude;
}
