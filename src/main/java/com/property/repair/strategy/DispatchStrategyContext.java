package com.property.repair.strategy;

import com.property.repair.common.exception.BusinessException;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.SysUser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DispatchStrategyContext {

    private final AutoDispatchStrategy autoDispatchStrategy;
    private final ManualDispatchStrategy manualDispatchStrategy;

    public DispatchStrategyContext(AutoDispatchStrategy autoDispatchStrategy,
                                  ManualDispatchStrategy manualDispatchStrategy) {
        this.autoDispatchStrategy = autoDispatchStrategy;
        this.manualDispatchStrategy = manualDispatchStrategy;
    }

    /**
     * Dispatch using the specified strategy type.
     *
     * @param type       "AUTO" or "MANUAL"
     * @param order      the repair order
     * @param candidates the list of candidate workers
     * @return the selected worker's ID, or null if no suitable worker found
     */
    public Long dispatch(String type, RepairOrder order, List<SysUser> candidates) {
        DispatchStrategy strategy;
        if ("AUTO".equalsIgnoreCase(type)) {
            strategy = autoDispatchStrategy;
        } else if ("MANUAL".equalsIgnoreCase(type)) {
            strategy = manualDispatchStrategy;
        } else {
            throw new BusinessException("不支持的派单类型: " + type);
        }
        return strategy.selectWorker(order, candidates);
    }
}
