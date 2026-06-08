package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.dto.request.ReworkRequest;

public interface ReworkService {

    Result<?> requestRework(Long orderId, ReworkRequest request);
}
