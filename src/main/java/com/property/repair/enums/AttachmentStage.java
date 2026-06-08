package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Attachment upload stage.
 */
@Getter
@AllArgsConstructor
public enum AttachmentStage {

    SUBMIT("SUBMIT", "Uploaded during order submission"),
    PROCESS("PROCESS", "Uploaded during repair process"),
    COMPLETE("COMPLETE", "Uploaded at completion (evidence)"),
    REWORK("REWORK", "Uploaded for rework request");

    private final String code;
    private final String description;
}
