package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.Result;
import com.property.repair.entity.Community;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.TimeoutEscalation;
import com.property.repair.mapper.CommunityMapper;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.TimeoutEscalationMapper;
import com.property.repair.service.DispatchService;
import com.property.repair.service.TimeoutEscalationService;
import com.property.repair.statemachine.OrderStateMachine;
import com.property.repair.statemachine.StateChangeListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TimeoutEscalationServiceImpl implements TimeoutEscalationService {

    private final TimeoutEscalationMapper timeoutEscalationMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final CommunityMapper communityMapper;
    private final OrderStateMachine orderStateMachine;
    private final StateChangeListener stateChangeListener;
    private final DispatchService dispatchService;

    public TimeoutEscalationServiceImpl(TimeoutEscalationMapper timeoutEscalationMapper,
                                        RepairOrderMapper repairOrderMapper,
                                        CommunityMapper communityMapper,
                                        OrderStateMachine orderStateMachine,
                                        StateChangeListener stateChangeListener,
                                        DispatchService dispatchService) {
        this.timeoutEscalationMapper = timeoutEscalationMapper;
        this.repairOrderMapper = repairOrderMapper;
        this.communityMapper = communityMapper;
        this.orderStateMachine = orderStateMachine;
        this.stateChangeListener = stateChangeListener;
        this.dispatchService = dispatchService;
    }

    @Override
    @Transactional
    public void escalateOrder(RepairOrder order, Long workerId) {
        Community community = communityMapper.selectById(order.getCommunityId());
        Long supervisorId = (community != null) ? community.getSupervisorId() : null;

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = orderStateMachine.fire(order, OrderEvent.ESCALATE, null, null);

        int timeoutMinutes = 0;
        if (order.getAssignedAt() != null) {
            timeoutMinutes = (int) ChronoUnit.MINUTES.between(order.getAssignedAt(), LocalDateTime.now());
        }

        TimeoutEscalation escalation = new TimeoutEscalation();
        escalation.setOrderId(order.getId());
        escalation.setWorkerId(workerId);
        escalation.setSupervisorId(supervisorId);
        escalation.setEscalationType("ACCEPT_TIMEOUT");
        escalation.setTimeoutMinutes(timeoutMinutes);
        timeoutEscalationMapper.insert(escalation);

        order.setStatus(toStatus);
        repairOrderMapper.updateById(order);

        stateChangeListener.onStateChange(order, fromStatus, toStatus, null,
                "接单超时自动升级，超时" + timeoutMinutes + "分钟");
    }

    @Override
    @Transactional
    public Result<?> resolveEscalation(Long escalationId, Long newWorkerId) {
        TimeoutEscalation escalation = timeoutEscalationMapper.selectById(escalationId);
        if (escalation == null) {
            throw new BusinessException("升级记录不存在");
        }

        if (escalation.getResolvedAt() != null) {
            throw new BusinessException("该升级记录已处理");
        }

        escalation.setResolution("已重新分配维修人员");
        escalation.setResolvedAt(LocalDateTime.now());
        timeoutEscalationMapper.updateById(escalation);

        dispatchService.reassign(escalation.getOrderId(), newWorkerId,
                escalation.getSupervisorId(), "超时升级后重新分配");

        return Result.success("升级工单已处理");
    }

    @Override
    public Result<List<TimeoutEscalation>> getPendingEscalations(Long supervisorId) {
        LambdaQueryWrapper<TimeoutEscalation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TimeoutEscalation::getSupervisorId, supervisorId)
                .isNull(TimeoutEscalation::getResolvedAt)
                .orderByDesc(TimeoutEscalation::getCreatedAt);
        List<TimeoutEscalation> escalations = timeoutEscalationMapper.selectList(wrapper);
        return Result.success(escalations);
    }
}
