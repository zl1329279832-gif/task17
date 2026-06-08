package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.TimeoutEscalation;

import java.util.List;

public interface TimeoutEscalationService {

    void escalateOrder(RepairOrder order, Long workerId);

    Result<?> resolveEscalation(Long escalationId, Long newWorkerId);

    Result<List<TimeoutEscalation>> getPendingEscalations(Long supervisorId);
}
