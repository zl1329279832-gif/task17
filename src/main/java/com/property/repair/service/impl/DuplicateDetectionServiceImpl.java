package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.dto.response.DuplicateCheckResponse;
import com.property.repair.entity.RepairOrder;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.service.DuplicateDetectionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DuplicateDetectionServiceImpl implements DuplicateDetectionService {

    private final RepairOrderMapper repairOrderMapper;

    public DuplicateDetectionServiceImpl(RepairOrderMapper repairOrderMapper) {
        this.repairOrderMapper = repairOrderMapper;
    }

    @Override
    public DuplicateCheckResponse checkDuplicate(Long communityId, Long buildingId, Long categoryId) {
        DuplicateCheckResponse response = new DuplicateCheckResponse();

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(48);

        List<OrderStatus> closedStatuses = Arrays.asList(OrderStatus.CONFIRMED, OrderStatus.EVALUATED);

        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RepairOrder::getCommunityId, communityId)
                .eq(RepairOrder::getBuildingId, buildingId)
                .eq(RepairOrder::getCategoryId, categoryId)
                .ge(RepairOrder::getCreatedAt, cutoffTime)
                .notIn(RepairOrder::getStatus, closedStatuses);

        List<RepairOrder> existingOrders = repairOrderMapper.selectList(wrapper);

        if (existingOrders.isEmpty()) {
            response.setDuplicate(false);
            response.setDuplicateOrders(new ArrayList<>());
        } else {
            response.setDuplicate(true);
            List<DuplicateCheckResponse.DuplicateOrderInfo> orderInfos = new ArrayList<>();
            for (RepairOrder order : existingOrders) {
                DuplicateCheckResponse.DuplicateOrderInfo info = new DuplicateCheckResponse.DuplicateOrderInfo();
                info.setId(order.getId());
                info.setOrderNo(order.getOrderNo());
                info.setTitle(order.getTitle());
                info.setStatus(order.getStatus().name());
                info.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");
                orderInfos.add(info);
            }
            response.setDuplicateOrders(orderInfos);
        }

        return response;
    }

    @Override
    public void linkDuplicates(Long orderId, String duplicateGroup) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order != null) {
            if (duplicateGroup == null || duplicateGroup.isEmpty()) {
                duplicateGroup = "DG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
            order.setDuplicateGroup(duplicateGroup);
            repairOrderMapper.updateById(order);
        }
    }
}
