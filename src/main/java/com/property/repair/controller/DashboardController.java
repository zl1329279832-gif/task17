package com.property.repair.controller;

import com.property.repair.common.result.Result;
import com.property.repair.dto.response.DashboardStatsResponse;
import com.property.repair.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public Result<DashboardStatsResponse> getStats() {
        return dashboardService.getStats();
    }
}
