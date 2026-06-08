package com.property.repair.statemachine;

import com.property.repair.common.enums.OrderStatus;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.RepairProgress;
import com.property.repair.mapper.RepairProgressMapper;
import org.springframework.stereotype.Component;

@Component
public class StateChangeListener {

    private final RepairProgressMapper repairProgressMapper;

    public StateChangeListener(RepairProgressMapper repairProgressMapper) {
        this.repairProgressMapper = repairProgressMapper;
    }

    /**
     * Record a state change in repair_progress.
     *
     * @param order      the repair order
     * @param fromStatus the previous status
     * @param toStatus   the new status
     * @param operatorId the operator's user ID
     * @param remark     optional remark
     */
    public void onStateChange(RepairOrder order, OrderStatus fromStatus, OrderStatus toStatus,
                              Long operatorId, String remark) {
        RepairProgress progress = new RepairProgress();
        progress.setOrderId(order.getId());
        progress.setFromStatus(fromStatus.name());
        progress.setToStatus(toStatus.name());
        progress.setOperatorId(operatorId);
        progress.setRemark(remark);
        repairProgressMapper.insert(progress);
    }
}
