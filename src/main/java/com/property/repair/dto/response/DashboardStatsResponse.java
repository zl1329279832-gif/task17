package com.property.repair.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class DashboardStatsResponse {

    private Long totalOrders;
    private Long pendingOrders;
    private Long inProgressOrders;
    private Long completedOrders;
    private Long escalatedOrders;
    private Long reworkOrders;
    private Double avgCompletionHours;
    private Double avgEvaluationScore;
    private Map<String, Long> ordersByStatus;
    private Map<String, Long> ordersByCategory;
}
