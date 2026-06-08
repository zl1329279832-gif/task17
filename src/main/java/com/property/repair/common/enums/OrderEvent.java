package com.property.repair.common.enums;

import lombok.Getter;

@Getter
public enum OrderEvent {

    DISPATCH("派单"),
    ACCEPT("接单"),
    REJECT_ACCEPT("拒绝接单"),
    START_REPAIR("开始维修"),
    SUSPEND("挂起"),
    RESUME("恢复"),
    COMPLETE("完工"),
    OWNER_CONFIRM("业主确认"),
    OWNER_REJECT("业主拒绝"),
    EVALUATE("评价"),
    REQUEST_REWORK("发起返工"),
    ESCALATE("超时升级"),
    REASSIGN("重新派单");

    private final String description;

    OrderEvent(String description) {
        this.description = description;
    }
}
