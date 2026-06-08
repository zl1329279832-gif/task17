package com.property.repair.service;

import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.result.PageResult;
import com.property.repair.common.result.Result;
import com.property.repair.dto.request.RepairOrderCreateRequest;
import com.property.repair.dto.request.RepairOrderQueryRequest;
import com.property.repair.dto.request.SuspendRequest;
import com.property.repair.dto.response.RepairOrderDetailResponse;
import com.property.repair.dto.response.RepairOrderListResponse;

import java.util.List;

public interface RepairOrderService {

    Result<?> createOrder(RepairOrderCreateRequest request);

    Result<RepairOrderDetailResponse> getOrderDetail(Long id);

    Result<PageResult<RepairOrderListResponse>> getOrderList(RepairOrderQueryRequest request);

    Result<?> performAction(Long orderId, OrderEvent event, String remark);

    Result<?> suspendOrder(Long orderId, SuspendRequest request);

    Result<?> completeOrder(Long orderId, String summary, List<Long> attachmentIds);
}
