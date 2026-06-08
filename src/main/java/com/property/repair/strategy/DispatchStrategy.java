package com.property.repair.strategy;

import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.SysUser;

import java.util.List;

public interface DispatchStrategy {

    /**
     * Select a worker from the candidate list for the given order.
     *
     * @param order      the repair order
     * @param candidates the list of candidate workers
     * @return the selected worker's ID, or null if no suitable worker found
     */
    Long selectWorker(RepairOrder order, List<SysUser> candidates);
}
