package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.result.Result;
import com.property.repair.entity.RepairProgress;
import com.property.repair.mapper.RepairProgressMapper;
import com.property.repair.service.RepairProgressService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RepairProgressServiceImpl implements RepairProgressService {

    private final RepairProgressMapper repairProgressMapper;

    public RepairProgressServiceImpl(RepairProgressMapper repairProgressMapper) {
        this.repairProgressMapper = repairProgressMapper;
    }

    @Override
    public Result<List<RepairProgress>> getByOrderId(Long orderId) {
        LambdaQueryWrapper<RepairProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RepairProgress::getOrderId, orderId)
                .orderByAsc(RepairProgress::getCreatedAt);
        List<RepairProgress> progressList = repairProgressMapper.selectList(wrapper);
        return Result.success(progressList);
    }
}
