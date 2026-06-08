package com.property.repair.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RepairOrderCreateRequest {

    @NotNull(message = "小区ID不能为空")
    private Long communityId;

    @NotNull(message = "楼栋ID不能为空")
    private Long buildingId;

    @NotBlank(message = "单元号不能为空")
    private String unitNumber;

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    @NotBlank(message = "描述不能为空")
    private String description;

    private Integer urgency = 1;

    private LocalDateTime expectedTime;

    private List<Long> attachmentIds;

    private Boolean forceCreate = false;
}
