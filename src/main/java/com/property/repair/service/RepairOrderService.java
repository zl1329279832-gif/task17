package com.property.repair.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.property.repair.common.PageResult;
import com.property.repair.dto.*;
import com.property.repair.entity.RepairOrder;

public interface RepairOrderService extends IService<RepairOrder> {

    /**
     * Owner submits a repair order. Triggers duplicate detection and auto-dispatch.
     */
    RepairOrderVO submitOrder(RepairOrderSubmitRequest request, Long ownerId);

    /**
     * Get order detail with all related data.
     */
    RepairOrderVO getOrderDetail(Long orderId);

    /**
     * Query orders with pagination and filters.
     */
    PageResult<RepairOrderVO> queryOrders(RepairOrderQueryRequest query);

    /**
     * Admin/supervisor manually assigns a worker.
     */
    void manualDispatch(Long orderId, ManualDispatchRequest request);

    /**
     * Worker accepts the assigned order.
     */
    void acceptOrder(Long orderId, Long workerId);

    /**
     * Worker rejects the order (triggers re-dispatch).
     */
    void rejectOrder(Long orderId, Long workerId, String reason);

    /**
     * Worker transfers the order to another worker.
     */
    void transferOrder(Long orderId, TransferRequest request);

    /**
     * Worker records on-site visit.
     */
    void visitOrder(Long orderId, Long workerId);

    /**
     * Worker suspends the order temporarily.
     */
    void suspendOrder(Long orderId, SuspendRequest request);

    /**
     * Worker resumes a suspended order.
     */
    void resumeOrder(Long orderId, Long workerId);

    /**
     * Worker completes the repair.
     */
    void completeOrder(Long orderId, CompleteRequest request);

    /**
     * Owner reviews the completed repair.
     */
    void reviewOrder(Long orderId, ReviewRequest request);

    /**
     * Owner requests rework after completion.
     */
    void requestRework(Long orderId, ReworkRequest request);

    /**
     * Owner confirms and closes the order.
     */
    void confirmOrder(Long orderId, Long ownerId);

    /**
     * Admin closes the order.
     */
    void closeOrder(Long orderId, String reason);

    /**
     * Owner cancels the order.
     */
    void cancelOrder(Long orderId, Long ownerId);

    /**
     * Merge a duplicate order into a parent order.
     */
    void mergeDuplicateOrder(Long duplicateOrderId, Long parentOrderId);
}
