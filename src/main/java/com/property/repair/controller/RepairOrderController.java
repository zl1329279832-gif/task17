package com.property.repair.controller;

import com.property.repair.common.annotation.Idempotent;
import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.result.PageResult;
import com.property.repair.common.result.Result;
import com.property.repair.dto.request.*;
import com.property.repair.dto.response.DuplicateCheckResponse;
import com.property.repair.dto.response.RepairOrderDetailResponse;
import com.property.repair.dto.response.RepairOrderListResponse;
import com.property.repair.entity.RepairProgress;
import com.property.repair.service.*;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/repair-orders")
public class RepairOrderController {

    private final RepairOrderService repairOrderService;
    private final ReworkService reworkService;
    private final EvaluationService evaluationService;
    private final RepairProgressService repairProgressService;
    private final DuplicateDetectionService duplicateDetectionService;

    public RepairOrderController(RepairOrderService repairOrderService,
                                 ReworkService reworkService,
                                 EvaluationService evaluationService,
                                 RepairProgressService repairProgressService,
                                 DuplicateDetectionService duplicateDetectionService) {
        this.repairOrderService = repairOrderService;
        this.reworkService = reworkService;
        this.evaluationService = evaluationService;
        this.repairProgressService = repairProgressService;
        this.duplicateDetectionService = duplicateDetectionService;
    }

    @Idempotent
    @PostMapping
    public Result<?> createOrder(@Valid @RequestBody RepairOrderCreateRequest request) {
        return repairOrderService.createOrder(request);
    }

    @GetMapping
    public Result<PageResult<RepairOrderListResponse>> getOrderList(RepairOrderQueryRequest request) {
        return repairOrderService.getOrderList(request);
    }

    @GetMapping("/{id}")
    public Result<RepairOrderDetailResponse> getOrderDetail(@PathVariable Long id) {
        return repairOrderService.getOrderDetail(id);
    }

    @PostMapping("/{id}/accept")
    public Result<?> accept(@PathVariable Long id) {
        return repairOrderService.performAction(id, OrderEvent.ACCEPT, null);
    }

    @PostMapping("/{id}/start")
    public Result<?> start(@PathVariable Long id) {
        return repairOrderService.performAction(id, OrderEvent.START_REPAIR, null);
    }

    @PostMapping("/{id}/suspend")
    public Result<?> suspend(@PathVariable Long id, @Valid @RequestBody SuspendRequest request) {
        return repairOrderService.suspendOrder(id, request);
    }

    @PostMapping("/{id}/resume")
    public Result<?> resume(@PathVariable Long id) {
        return repairOrderService.performAction(id, OrderEvent.RESUME, null);
    }

    @PostMapping("/{id}/complete")
    public Result<?> complete(@PathVariable Long id,
                              @RequestParam(required = false) String summary,
                              @RequestParam(required = false) List<Long> attachmentIds) {
        return repairOrderService.completeOrder(id, summary, attachmentIds);
    }

    @PostMapping("/{id}/confirm")
    public Result<?> confirm(@PathVariable Long id) {
        return repairOrderService.performAction(id, OrderEvent.OWNER_CONFIRM, null);
    }

    @PostMapping("/{id}/reject")
    public Result<?> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return repairOrderService.performAction(id, OrderEvent.OWNER_REJECT, reason);
    }

    @PostMapping("/{id}/rework")
    public Result<?> rework(@PathVariable Long id, @Valid @RequestBody ReworkRequest request) {
        return reworkService.requestRework(id, request);
    }

    @PostMapping("/{id}/evaluate")
    public Result<?> evaluate(@PathVariable Long id, @Valid @RequestBody EvaluationRequest request) {
        return evaluationService.evaluate(id, request);
    }

    @GetMapping("/{id}/timeline")
    public Result<List<RepairProgress>> getTimeline(@PathVariable Long id) {
        return repairProgressService.getByOrderId(id);
    }

    @PostMapping("/duplicate-check")
    public Result<DuplicateCheckResponse> checkDuplicate(@RequestParam Long communityId,
                                                         @RequestParam Long buildingId,
                                                         @RequestParam Long categoryId) {
        DuplicateCheckResponse response = duplicateDetectionService.checkDuplicate(communityId, buildingId, categoryId);
        return Result.success(response);
    }
}
