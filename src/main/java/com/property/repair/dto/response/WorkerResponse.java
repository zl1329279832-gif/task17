package com.property.repair.dto.response;

import lombok.Data;

@Data
public class WorkerResponse {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private Integer onlineStatus;
    private Integer activeOrderCount;
    private Integer totalScore;
    private Integer skillProficiency;
    private String communityName;
}
