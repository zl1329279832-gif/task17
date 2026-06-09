package com.property.repair.controller;

import com.property.repair.common.Result;
import com.property.repair.dto.*;
import com.property.repair.security.RequireRole;
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

    /**
     * Get recommended spare parts for an order.
     */
    @GetMapping("/recommend/{orderId}")
    @PreAuthorize("hasAnyRole('WORKER', 'SUPERVISOR', 'ADMIN')")
    public Result<List<SparePartRecommendVO>> recommendParts(@PathVariable Long orderId) {
        return Result.ok(sparePartService.recommendParts(orderId));
    }

    /**
     * Worker submits a spare part requisition.
     */
    @PostMapping("/requisitions")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> submitRequisition(@Valid @RequestBody SparePartRequisitionRequest request) {
        Long workerId = SecurityUtils.getCurrentUserId();
        sparePartService.submitRequisition(request, workerId);
        return Result.ok();
    }

    /**
     * Worker returns unused parts.
     */
    @PostMapping("/requisitions/{requisitionId}/return")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> returnParts(@PathVariable Long requisitionId,
                                     @Valid @RequestBody SparePartReturnRequest request) {
        Long workerId = SecurityUtils.getCurrentUserId();
        sparePartService.returnParts(requisitionId, request, workerId);
        return Result.ok();
    }

    /**
     * Admin confirms purchase arrival.
     */
    @PostMapping("/purchases/{purchaseId}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Result<Void> receivePurchase(@PathVariable Long purchaseId,
                                         @Valid @RequestBody PurchaseReceiveRequest request) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        sparePartService.receivePurchase(purchaseId, request, operatorId);
        return Result.ok();
    }
}
