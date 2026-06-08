package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.entity.RepairProgress;

import java.util.List;

public interface RepairProgressService {

    Result<List<RepairProgress>> getByOrderId(Long orderId);
}
