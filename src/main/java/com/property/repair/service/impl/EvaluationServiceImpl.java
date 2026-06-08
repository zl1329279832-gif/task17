package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.Result;
import com.property.repair.dto.request.EvaluationRequest;
import com.property.repair.entity.Evaluation;
import com.property.repair.entity.RepairOrder;
import com.property.repair.mapper.EvaluationMapper;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.EvaluationService;
import com.property.repair.statemachine.OrderStateMachine;
import com.property.repair.statemachine.StateChangeListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationMapper evaluationMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final OrderStateMachine orderStateMachine;
    private final StateChangeListener stateChangeListener;

    public EvaluationServiceImpl(EvaluationMapper evaluationMapper,
                                 RepairOrderMapper repairOrderMapper,
                                 OrderStateMachine orderStateMachine,
                                 StateChangeListener stateChangeListener) {
        this.evaluationMapper = evaluationMapper;
        this.repairOrderMapper = repairOrderMapper;
        this.orderStateMachine = orderStateMachine;
        this.stateChangeListener = stateChangeListener;
    }

    @Override
    @Transactional
    public Result<?> evaluate(Long orderId, EvaluationRequest request) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException("只有已确认的报修单才能评价");
        }

        LambdaQueryWrapper<Evaluation> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(Evaluation::getOrderId, orderId);
        Long existCount = evaluationMapper.selectCount(existWrapper);
        if (existCount > 0) {
            throw new BusinessException("该报修单已评价，不能重复评价");
        }

        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(orderId);
        evaluation.setOwnerId(currentUser.getUserId());
        evaluation.setWorkerId(order.getCurrentWorkerId());
        evaluation.setScore(request.getScore());
        evaluation.setAttitudeScore(request.getAttitudeScore());
        evaluation.setQualityScore(request.getQualityScore());
        evaluation.setSpeedScore(request.getSpeedScore());
        evaluation.setComment(request.getComment());
        evaluationMapper.insert(evaluation);

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = orderStateMachine.fire(order, OrderEvent.EVALUATE, currentUser.getUserId(), currentUser.getRole());
        order.setStatus(toStatus);
        repairOrderMapper.updateById(order);

        stateChangeListener.onStateChange(order, fromStatus, toStatus, currentUser.getUserId(), "评价完成");

        return Result.success("评价成功");
    }

    @Override
    public Result<List<Evaluation>> getByWorkerId(Long workerId) {
        LambdaQueryWrapper<Evaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Evaluation::getWorkerId, workerId)
                .orderByDesc(Evaluation::getCreatedAt);
        List<Evaluation> evaluations = evaluationMapper.selectList(wrapper);
        return Result.success(evaluations);
    }
}
