package com.property.repair.service;

import com.property.repair.dto.request.DispatchRequest;
import com.property.repair.dto.response.WorkerResponse;
import com.property.repair.entity.RepairOrder;

import java.util.List;

public interface DispatchService {

    Long autoDispatch(RepairOrder order);

    void manualDispatch(DispatchRequest request);

    List<WorkerResponse> getCandidates(Long orderId);

    void reassign(Long orderId, Long workerId, Long operatorId, String reason);
}
