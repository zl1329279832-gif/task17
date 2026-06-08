package com.property.repair.service;

import com.property.repair.dto.DispatchCandidate;
import com.property.repair.entity.RepairOrder;

import java.util.List;

/**
 * Dispatch strategy interface — pluggable auto-dispatch algorithms.
 */
public interface DispatchStrategy {

    /**
     * Find the best worker for a given repair order.
     *
     * @param order the repair order to dispatch
     * @return the best matching worker ID, or null if no worker available
     */
    Long findBestWorker(RepairOrder order);

    /**
     * Get ranked candidate list for display/debugging.
     */
    List<DispatchCandidate> getCandidates(RepairOrder order);
}
