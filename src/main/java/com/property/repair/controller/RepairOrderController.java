package com.property.repair.controller;

import com.property.repair.common.PageResult;
import com.property.repair.common.Result;
import com.property.repair.dto.*;
import com.property.repair.security.RequireRole;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.RepairOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class RepairOrderController {

    private final RepairOrderService orderService;

    // ==========================================================================
    // Owner Endpoints
    // ==========================================================================

    /**
     * Submit a new repair order (Owner only).
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public Result<RepairOrderVO> submitOrder(@Valid @RequestBody RepairOrderSubmitRequest request) {
        Long ownerId = SecurityUtils.getCurrentUserId();
        return Result.ok(orderService.submitOrder(request, ownerId));
    }

    /**
     * Get order detail.
     */
    @GetMapping("/{orderId}")
    public Result<RepairOrderVO> getOrderDetail(@PathVariable Long orderId) {
        return Result.ok(orderService.getOrderDetail(orderId));
    }

    /**
     * Query orders with pagination.
     */
    @GetMapping
    public Result<PageResult<RepairOrderVO>> queryOrders(RepairOrderQueryRequest query) {
        // Auto-filter by current user role
        String role = SecurityUtils.getCurrentRole();
        Long userId = SecurityUtils.getCurrentUserId();

        if ("OWNER".equals(role)) {
            query.setOwnerId(userId);
        } else if ("WORKER".equals(role)) {
            query.setWorkerId(userId);
        }
        // Supervisor and Admin can see all

        return Result.ok(orderService.queryOrders(query));
    }

    /**
     * Owner reviews a completed order.
     */
    @PostMapping("/{orderId}/review")
    @PreAuthorize("hasRole('OWNER')")
    public Result<Void> reviewOrder(@PathVariable Long orderId,
                                     @Valid @RequestBody ReviewRequest request) {
        orderService.reviewOrder(orderId, request);
        return Result.ok();
    }

    /**
     * Owner requests rework after completion.
     */
    @PostMapping("/{orderId}/rework")
    @PreAuthorize("hasRole('OWNER')")
    public Result<Void> requestRework(@PathVariable Long orderId,
                                       @Valid @RequestBody ReworkRequest request) {
        orderService.requestRework(orderId, request);
        return Result.ok();
    }

    /**
     * Owner confirms order completion.
     */
    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasRole('OWNER')")
    public Result<Void> confirmOrder(@PathVariable Long orderId) {
        orderService.confirmOrder(orderId, SecurityUtils.getCurrentUserId());
        return Result.ok();
    }

    /**
     * Owner cancels a pending order.
     */
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('OWNER')")
    public Result<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId, SecurityUtils.getCurrentUserId());
        return Result.ok();
    }

    // ==========================================================================
    // Worker Endpoints
    // ==========================================================================

    /**
     * Worker accepts the assigned order.
     */
    @PostMapping("/{orderId}/accept")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> acceptOrder(@PathVariable Long orderId) {
        orderService.acceptOrder(orderId, SecurityUtils.getCurrentUserId());
        return Result.ok();
    }

    /**
     * Worker rejects the assigned order.
     */
    @PostMapping("/{orderId}/reject")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> rejectOrder(@PathVariable Long orderId,
                                     @RequestParam String reason) {
        orderService.rejectOrder(orderId, SecurityUtils.getCurrentUserId(), reason);
        return Result.ok();
    }

    /**
     * Worker transfers the order to another worker.
     */
    @PostMapping("/{orderId}/transfer")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> transferOrder(@PathVariable Long orderId,
                                       @Valid @RequestBody TransferRequest request) {
        orderService.transferOrder(orderId, request);
        return Result.ok();
    }

    /**
     * Worker records on-site visit.
     */
    @PostMapping("/{orderId}/visit")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> visitOrder(@PathVariable Long orderId) {
        orderService.visitOrder(orderId, SecurityUtils.getCurrentUserId());
        return Result.ok();
    }

    /**
     * Worker suspends the order.
     */
    @PostMapping("/{orderId}/suspend")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> suspendOrder(@PathVariable Long orderId,
                                      @Valid @RequestBody SuspendRequest request) {
        orderService.suspendOrder(orderId, request);
        return Result.ok();
    }

    /**
     * Worker resumes a suspended order.
     */
    @PostMapping("/{orderId}/resume")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> resumeOrder(@PathVariable Long orderId) {
        orderService.resumeOrder(orderId, SecurityUtils.getCurrentUserId());
        return Result.ok();
    }

    /**
     * Worker completes the repair.
     */
    @PostMapping("/{orderId}/complete")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> completeOrder(@PathVariable Long orderId,
                                       @Valid @RequestBody CompleteRequest request) {
        orderService.completeOrder(orderId, request);
        return Result.ok();
    }

    // ==========================================================================
    // Admin / Supervisor Endpoints
    // ==========================================================================

    /**
     * Manually dispatch an order to a specific worker.
     */
    @PostMapping("/{orderId}/dispatch")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<Void> manualDispatch(@PathVariable Long orderId,
                                        @Valid @RequestBody ManualDispatchRequest request) {
        orderService.manualDispatch(orderId, request);
        return Result.ok();
    }

    /**
     * Close an order (admin only).
     */
    @PostMapping("/{orderId}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> closeOrder(@PathVariable Long orderId,
                                    @RequestParam String reason) {
        orderService.closeOrder(orderId, reason);
        return Result.ok();
    }

    /**
     * Merge a duplicate order into a parent.
     */
    @PostMapping("/{duplicateOrderId}/merge/{parentOrderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<Void> mergeDuplicate(@PathVariable Long duplicateOrderId,
                                        @PathVariable Long parentOrderId) {
        orderService.mergeDuplicateOrder(duplicateOrderId, parentOrderId);
        return Result.ok();
    }
}
