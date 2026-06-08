package com.property.repair.strategy;

import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.SysUser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("manualDispatchStrategy")
public class ManualDispatchStrategy implements DispatchStrategy {

    @Override
    public Long selectWorker(RepairOrder order, List<SysUser> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(0).getId();
    }
}
