package com.property.repair.controller;

import com.property.repair.common.Result;
import com.property.repair.dto.*;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.SparePartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/spare-parts")
@RequiredArgsConstructor
public class SparePartController {

    private final SparePartService sparePartService;

    // ==========================================================================
    // Worker Endpoints
    // ==========================================================================

    /**
     * Get spare part recommendations for a repair order.
     */
    @GetMapping("/recommend/{orderId}")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN', 'SUPERVISOR')")
    public Result<PartRecommendationVO> recommendParts(@PathVariable Long orderId) {
        return Result.ok(sparePartService.recommendParts(orderId));
    }

    /**
     * Worker creates a part request for a repair order.
     */
    @PostMapping("/requests/{orderId}")
    @PreAuthorize("hasRole('WORKER')")
    public Result<PartRequestVO> createPartRequest(@PathVariable Long orderId,
                                                     @Valid @RequestBody PartRequestCreateRequest request) {
        Long workerId = SecurityUtils.getCurrentUserId();
        return Result.ok(sparePartService.createPartRequest(orderId, request, workerId));
    }

    /**
     * Get part request detail.
     */
    @GetMapping("/requests/{requestId}")
    public Result<PartRequestVO> getPartRequestDetail(@PathVariable Long requestId) {
        return Result.ok(sparePartService.getPartRequestDetail(requestId));
    }

    /**
     * List all part requests for an order.
     */
    @GetMapping("/requests/order/{orderId}")
    public Result<List<PartRequestVO>> listPartRequests(@PathVariable Long orderId) {
        return Result.ok(sparePartService.listPartRequests(orderId));
    }

    /**
     * Worker returns unused parts.
     */
    @PostMapping("/requests/{requestId}/return")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> returnParts(@PathVariable Long requestId,
                                     @Valid @RequestBody ReturnPartsRequest request) {
        Long workerId = SecurityUtils.getCurrentUserId();
        sparePartService.returnParts(requestId, request, workerId);
        return Result.ok();
    }

    /**
     * Worker records part consumption.
     */
    @PostMapping("/requests/{requestId}/consume")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> consumeParts(@PathVariable Long requestId,
                                      @Valid @RequestBody ConsumePartsRequest request) {
        Long workerId = SecurityUtils.getCurrentUserId();
        sparePartService.consumeParts(requestId, request, workerId);
        return Result.ok();
    }

    // ==========================================================================
    // Admin / Supervisor Endpoints
    // ==========================================================================

    /**
     * Approve a part request.
     */
    @PostMapping("/requests/{requestId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<Void> approvePartRequest(@PathVariable Long requestId) {
        Long approverId = SecurityUtils.getCurrentUserId();
        sparePartService.approvePartRequest(requestId, approverId);
        return Result.ok();
    }

    /**
     * Issue parts against an approved request.
     */
    @PostMapping("/requests/{requestId}/issue")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<Void> issueParts(@PathVariable Long requestId,
                                    @Valid @RequestBody IssuePartsRequest request) {
        Long issuerId = SecurityUtils.getCurrentUserId();
        sparePartService.issueParts(requestId, request, issuerId);
        return Result.ok();
    }

    /**
     * Query inventory for a community/building.
     */
    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<List<SparePartInventoryVO>> queryInventory(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) Long buildingId) {
        return Result.ok(sparePartService.queryInventory(communityId, buildingId));
    }

    // ==========================================================================
    // Purchase Request Endpoints
    // ==========================================================================

    /**
     * Create a purchase request for restocking.
     */
    @PostMapping("/purchase")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN', 'SUPERVISOR')")
    public Result<PurchaseRequestVO> createPurchaseRequest(
            @Valid @RequestBody PurchaseRequestCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(sparePartService.createPurchaseRequest(request, userId));
    }

    /**
     * Approve a purchase request.
     */
    @PostMapping("/purchase/{purchaseId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> approvePurchaseRequest(@PathVariable Long purchaseId) {
        Long approverId = SecurityUtils.getCurrentUserId();
        sparePartService.approvePurchaseRequest(purchaseId, approverId);
        return Result.ok();
    }

    /**
     * Mark a purchase request as ordered.
     */
    @PostMapping("/purchase/{purchaseId}/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<Void> markPurchaseOrdered(@PathVariable Long purchaseId) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        sparePartService.markPurchaseOrdered(purchaseId, operatorId);
        return Result.ok();
    }

    /**
     * Receive purchased parts and restock inventory.
     * Auto-resumes any WAITING_PARTS orders if their parts are now available.
     */
    @PostMapping("/purchase/{purchaseId}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<Void> receivePurchase(@PathVariable Long purchaseId) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        sparePartService.receivePurchase(purchaseId, operatorId);
        return Result.ok();
    }
}
