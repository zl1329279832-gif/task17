package com.property.repair.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dispatch candidate info returned by auto-dispatch strategy.
 */
@Data
public class DispatchCandidate {

    private Long workerId;
    private String workerName;
    private String phone;
    private Integer currentLoad;
    private Integer skillProficiency;
    private BigDecimal matchScore;
    private Integer onlineStatus;
    private LocalDateTime lastOnlineAt;
}
