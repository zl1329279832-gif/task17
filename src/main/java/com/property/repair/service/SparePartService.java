package com.property.repair.service;

import com.property.repair.dto.*;

import java.util.List;

public interface SparePartService {

    /**
     * Recommend spare parts based on order's problem type, location and history.
     */
    List<SparePartRecommendVO> recommendParts(Long orderId);

    /**
     * Worker submits a spare part requisition.
     * May trigger WAITING_PARTS if critical parts are insufficient.
     */
    void submitRequisition(SparePartRequisitionRequest request, Long workerId);

    /**
     * Worker returns unused parts from a requisition.
     */
    void returnParts(Long requisitionId, SparePartReturnRequest request, Long workerId);

    /**
     * Mark all issued parts as consumed when order is completed.
     */
    void consumePartsForOrder(Long orderId);

    /**
     * Admin confirms purchase arrival — increases inventory and
     * auto-resumes any WAITING_PARTS orders that can now be fulfilled.
     */
    void receivePurchase(Long purchaseId, PurchaseReceiveRequest request, Long operatorId);
}
