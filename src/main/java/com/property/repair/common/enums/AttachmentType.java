package com.property.repair.common.enums;

import lombok.Getter;

@Getter
public enum AttachmentType {

    REPORT("报修附件"),
    PROGRESS("进度附件"),
    COMPLETION("完工附件");

    private final String description;

    AttachmentType(String description) {
        this.description = description;
    }
}
