package com.property.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RepairOrderSubmitRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Problem type is required")
    private String problemType;

    /** 1=Low, 2=Normal, 3=High, 4=Urgent */
    @NotNull(message = "Urgency is required")
    private Integer urgency;

    @NotNull(message = "Community ID is required")
    private Long communityId;

    private Long buildingId;

    private Long unitId;

    private String roomNo;

    private String addressDetail;

    /** Attachment IDs already uploaded */
    private List<Long> attachmentIds;
}
