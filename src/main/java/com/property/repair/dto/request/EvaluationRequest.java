package com.property.repair.dto.request;

import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class EvaluationRequest {

    @NotNull(message = "总评分不能为空")
    @Min(value = 1, message = "评分最低为1")
    @Max(value = 5, message = "评分最高为5")
    private Integer score;

    @Min(value = 1, message = "态度评分最低为1")
    @Max(value = 5, message = "态度评分最高为5")
    private Integer attitudeScore;

    @Min(value = 1, message = "质量评分最低为1")
    @Max(value = 5, message = "质量评分最高为5")
    private Integer qualityScore;

    @Min(value = 1, message = "速度评分最低为1")
    @Max(value = 5, message = "速度评分最高为5")
    private Integer speedScore;

    private String comment;
}
