package com.property.repair.common.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING("待派单"),
    ASSIGNED("已派单"),
    ACCEPTED("已接单"),
    IN_PROGRESS("维修中"),
    SUSPENDED("已挂起"),
    COMPLETED("已完工"),
    CONFIRMED("业主确认"),
    EVALUATED("已评价"),
    ESCALATED("已升级"),
    REWORK("返工中"),
    REJECTED("已拒绝");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }
}
