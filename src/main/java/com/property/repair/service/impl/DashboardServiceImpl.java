package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.Result;
import com.property.repair.dto.response.DashboardStatsResponse;
import com.property.repair.entity.Community;
import com.property.repair.entity.RepairOrder;
import com.property.repair.mapper.CommunityMapper;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.DashboardService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final RepairOrderMapper repairOrderMapper;
    private final CommunityMapper communityMapper;

    public DashboardServiceImpl(RepairOrderMapper repairOrderMapper, CommunityMapper communityMapper) {
        this.repairOrderMapper = repairOrderMapper;
        this.communityMapper = communityMapper;
    }

    @Override
    public Result<DashboardStatsResponse> getStats() {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        applyDataScope(wrapper, currentUser);

        List<RepairOrder> orders = repairOrderMapper.selectList(wrapper);

        DashboardStatsResponse stats = new DashboardStatsResponse();
        stats.setTotalOrders((long) orders.size());

        stats.setPendingOrders(orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING).count());
        stats.setInProgressOrders(orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.IN_PROGRESS
                        || o.getStatus() == OrderStatus.ACCEPTED
                        || o.getStatus() == OrderStatus.ASSIGNED).count());
        stats.setCompletedOrders(orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED
                        || o.getStatus() == OrderStatus.CONFIRMED
                        || o.getStatus() == OrderStatus.EVALUATED).count());
        stats.setEscalatedOrders(orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.ESCALATED).count());
        stats.setReworkOrders(orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.REWORK).count());

        // Average completion time in hours
        double avgHours = orders.stream()
                .filter(o -> o.getCompletedAt() != null && o.getCreatedAt() != null)
                .mapToLong(o -> java.time.Duration.between(o.getCreatedAt(), o.getCompletedAt()).toHours())
                .average()
                .orElse(0.0);
        stats.setAvgCompletionHours(avgHours);

        // Orders by status
        Map<String, Long> byStatus = new HashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = orders.stream().filter(o -> o.getStatus() == status).count();
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }
        stats.setOrdersByStatus(byStatus);

        // Orders by category
        Map<String, Long> byCategory = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> String.valueOf(o.getCategoryId()),
                        Collectors.counting()));
        stats.setOrdersByCategory(byCategory);

        return Result.success(stats);
    }

    private void applyDataScope(LambdaQueryWrapper<RepairOrder> wrapper, LoginUser currentUser) {
        RoleType role = currentUser.getRole();
        switch (role) {
            case ADMIN:
                break;
            case SUPERVISOR:
                List<Community> communities = communityMapper.selectBySupervisorId(currentUser.getUserId());
                if (communities.isEmpty()) {
                    wrapper.eq(RepairOrder::getCommunityId, -1L);
                } else {
                    List<Long> communityIds = communities.stream()
                            .map(Community::getId)
                            .collect(Collectors.toList());
                    wrapper.in(RepairOrder::getCommunityId, communityIds);
                }
                break;
            case WORKER:
                wrapper.eq(RepairOrder::getCurrentWorkerId, currentUser.getUserId());
                break;
            case OWNER:
                wrapper.eq(RepairOrder::getOwnerId, currentUser.getUserId());
                break;
            default:
                throw new BusinessException(403, "无权限访问");
        }
    }
}
