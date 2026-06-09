package com.property.repair.service;

import com.property.repair.dto.*;

import java.util.List;

/**
 * Service for spare parts inventory management and repair order linkage.
 */
public interface SparePartService {

    /**
     * Recommend spare parts for a repair order based on problem type, building, and historical usage.
     */
    PartRecommendationVO recommendParts(Long orderId);

    /**
     * Worker creates a part request for a repair order.
     * If critical parts are insufficient, order enters WAITING_PARTS and SLA pauses.
     */
    PartRequestVO createPartRequest(Long orderId, PartRequestCreateRequest request, Long workerId);

    /**
     * Get part request detail.
     */
    PartRequestVO getPartRequestDetail(Long requestId);

    /**
     * List all part requests for an order.
     */
    List<PartRequestVO> listPartRequests(Long orderId);

    /**
     * Admin/supervisor approves a part request and reserves stock.
     */
    void approvePartRequest(Long requestId, Long approverId);

    /**
     * Admin/storekeeper issues parts against an approved request.
     * If all critical parts are now available and order is in WAITING_PARTS, auto-resume.
     */
    void issueParts(Long requestId, IssuePartsRequest request, Long issuerId);

    /**
     * Worker returns unused parts after repair completion.
     */
    void returnParts(Long requestId, ReturnPartsRequest request, Long workerId);

    /**
     * Worker records consumption of parts during repair.
     */
    void consumeParts(Long requestId, ConsumePartsRequest request, Long workerId);

    /**
     * Create a purchase request for restocking.
     * Auto-triggered when stock falls below safety level.
     */
    PurchaseRequestVO createPurchaseRequest(PurchaseRequestCreateRequest request, Long userId);

    /**
     * Approve a purchase request.
     */
    void approvePurchaseRequest(Long purchaseId, Long approverId);

    /**
     * Mark purchase as ordered (placed with supplier).
     */
    void markPurchaseOrdered(Long purchaseId, Long operatorId);

    /**
     * Receive purchased parts — increases inventory.
     * Checks if any WAITING_PARTS orders can be resumed.
     */
    void receivePurchase(Long purchaseId, Long operatorId);

    /**
     * Query inventory for a community/building.
     */
    List<SparePartInventoryVO> queryInventory(Long communityId, Long buildingId);

    /**
     * Check if a WAITING_PARTS order can be resumed (all critical parts now available).
     * If yes, auto-resume the order and notify the worker.
     */
    void checkAndResumeWaitingOrders(Long communityId, Long buildingId);
}
