package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.dto.response.DashboardStatsResponse;

public interface DashboardService {

    Result<DashboardStatsResponse> getStats();
}
