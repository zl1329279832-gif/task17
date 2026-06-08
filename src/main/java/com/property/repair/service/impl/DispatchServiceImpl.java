package com.property.repair.service.impl;

import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.dto.request.DispatchRequest;
import com.property.repair.dto.response.WorkerResponse;
import com.property.repair.entity.*;
import com.property.repair.mapper.*;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.DispatchService;
import com.property.repair.statemachine.OrderStateMachine;
import com.property.repair.statemachine.StateChangeListener;
import com.property.repair.strategy.DispatchStrategyContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DispatchServiceImpl implements DispatchService {

    private final DispatchStrategyContext dispatchStrategyContext;
    private final SysUserMapper sysUserMapper;
    private final WorkerSkillMapper workerSkillMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final DispatchRecordMapper dispatchRecordMapper;
    private final OrderStateMachine orderStateMachine;
    private final StateChangeListener stateChangeListener;

    public DispatchServiceImpl(DispatchStrategyContext dispatchStrategyContext,
                               SysUserMapper sysUserMapper,
                               WorkerSkillMapper workerSkillMapper,
                               RepairOrderMapper repairOrderMapper,
                               DispatchRecordMapper dispatchRecordMapper,
                               OrderStateMachine orderStateMachine,
                               StateChangeListener stateChangeListener) {
        this.dispatchStrategyContext = dispatchStrategyContext;
        this.sysUserMapper = sysUserMapper;
        this.workerSkillMapper = workerSkillMapper;
        this.repairOrderMapper = repairOrderMapper;
        this.dispatchRecordMapper = dispatchRecordMapper;
        this.orderStateMachine = orderStateMachine;
        this.stateChangeListener = stateChangeListener;
    }

    @Override
    @Transactional
    public Long autoDispatch(RepairOrder order) {
        List<WorkerSkill> matchingSkills = workerSkillMapper.selectByCommunityAndCategory(
                order.getCommunityId(), order.getCategoryId());

        if (matchingSkills.isEmpty()) {
            return null;
        }

        List<Long> workerIds = matchingSkills.stream()
                .map(WorkerSkill::getWorkerId)
                .distinct()
                .collect(Collectors.toList());

        List<SysUser> candidates = new ArrayList<>();
        for (Long workerId : workerIds) {
            SysUser worker = sysUserMapper.selectById(workerId);
            if (worker != null && worker.getStatus() == 1 && worker.getRole() == RoleType.WORKER) {
                candidates.add(worker);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Long selectedWorkerId = dispatchStrategyContext.dispatch("AUTO", order, candidates);

        if (selectedWorkerId == null) {
            return null;
        }

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = orderStateMachine.fire(order, OrderEvent.DISPATCH, null, null);

        order.setStatus(toStatus);
        order.setCurrentWorkerId(selectedWorkerId);
        order.setAssignedAt(LocalDateTime.now());
        repairOrderMapper.updateById(order);

        DispatchRecord record = new DispatchRecord();
        record.setOrderId(order.getId());
        record.setWorkerId(selectedWorkerId);
        record.setDispatchType("AUTO");
        record.setResult("DISPATCHED");
        dispatchRecordMapper.insert(record);

        stateChangeListener.onStateChange(order, fromStatus, toStatus, null, "系统自动派单");

        return selectedWorkerId;
    }

    @Override
    @Transactional
    public void manualDispatch(DispatchRequest request) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        RepairOrder order = repairOrderMapper.selectById(request.getOrderId());
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        SysUser worker = sysUserMapper.selectById(request.getWorkerId());
        if (worker == null || worker.getStatus() != 1 || worker.getRole() != RoleType.WORKER) {
            throw new BusinessException("指定的维修人员无效");
        }

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = orderStateMachine.fire(order, OrderEvent.DISPATCH,
                currentUser.getUserId(), currentUser.getRole());

        order.setStatus(toStatus);
        order.setCurrentWorkerId(request.getWorkerId());
        order.setAssignedAt(LocalDateTime.now());
        int rows = repairOrderMapper.updateById(order);
        if (rows == 0) {
            throw new BusinessException("操作失败，数据已被修改，请刷新后重试");
        }

        DispatchRecord record = new DispatchRecord();
        record.setOrderId(order.getId());
        record.setWorkerId(request.getWorkerId());
        record.setDispatcherId(currentUser.getUserId());
        record.setDispatchType("MANUAL");
        record.setReason(request.getReason());
        record.setResult("DISPATCHED");
        dispatchRecordMapper.insert(record);

        stateChangeListener.onStateChange(order, fromStatus, toStatus,
                currentUser.getUserId(), "手动派单: " + (request.getReason() != null ? request.getReason() : ""));
    }

    @Override
    public List<WorkerResponse> getCandidates(Long orderId) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        List<WorkerSkill> matchingSkills = workerSkillMapper.selectByCommunityAndCategory(
                order.getCommunityId(), order.getCategoryId());

        List<String> activeStatuses = Arrays.asList(
                OrderStatus.ASSIGNED.name(),
                OrderStatus.ACCEPTED.name(),
                OrderStatus.IN_PROGRESS.name(),
                OrderStatus.SUSPENDED.name()
        );

        List<WorkerResponse> responses = new ArrayList<>();
        for (WorkerSkill skill : matchingSkills) {
            SysUser worker = sysUserMapper.selectById(skill.getWorkerId());
            if (worker == null || worker.getStatus() != 1 || worker.getRole() != RoleType.WORKER) {
                continue;
            }

            WorkerResponse resp = new WorkerResponse();
            resp.setId(worker.getId());
            resp.setUsername(worker.getUsername());
            resp.setRealName(worker.getRealName());
            resp.setPhone(worker.getPhone());
            resp.setOnlineStatus(worker.getOnlineStatus());
            resp.setSkillProficiency(skill.getProficiency());

            int activeCount = repairOrderMapper.countByWorkerAndStatuses(worker.getId(), activeStatuses);
            resp.setActiveOrderCount(activeCount);

            int skillScore = skill.getProficiency() * 10;
            int workloadScore = Math.max(0, 30 - activeCount * 6);
            int onlineScore = (worker.getOnlineStatus() != null && worker.getOnlineStatus() == 1) ? 15 : 0;
            resp.setTotalScore(skillScore + workloadScore + 12 + onlineScore);

            responses.add(resp);
        }

        responses.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));

        return responses;
    }

    @Override
    @Transactional
    public void reassign(Long orderId, Long workerId, Long operatorId, String reason) {
        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        LoginUser currentUser = SecurityUtils.getCurrentUser();
        RoleType operatorRole = currentUser != null ? currentUser.getRole() : null;

        OrderStatus fromStatus = order.getStatus();
        OrderStatus afterReassign = orderStateMachine.fire(order, OrderEvent.REASSIGN, operatorId, operatorRole);
        order.setStatus(afterReassign);
        stateChangeListener.onStateChange(order, fromStatus, afterReassign, operatorId, "重新派单: " + reason);

        OrderStatus beforeDispatch = order.getStatus();
        OrderStatus afterDispatch = orderStateMachine.fire(order, OrderEvent.DISPATCH, operatorId, operatorRole);
        order.setStatus(afterDispatch);
        order.setCurrentWorkerId(workerId);
        order.setAssignedAt(LocalDateTime.now());

        int rows = repairOrderMapper.updateById(order);
        if (rows == 0) {
            throw new BusinessException("操作失败，数据已被修改，请刷新后重试");
        }

        stateChangeListener.onStateChange(order, beforeDispatch, afterDispatch, operatorId, "分配维修人员");

        String dispatchType = (fromStatus == OrderStatus.ESCALATED) ? "ESCALATION" : "TRANSFER";
        DispatchRecord record = new DispatchRecord();
        record.setOrderId(orderId);
        record.setWorkerId(workerId);
        record.setDispatcherId(operatorId);
        record.setDispatchType(dispatchType);
        record.setReason(reason);
        record.setResult("DISPATCHED");
        dispatchRecordMapper.insert(record);
    }
}
