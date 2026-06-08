package com.property.repair.service.impl;

import com.property.repair.common.constants.RepairConstants;
import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.Result;
import com.property.repair.dto.request.ReworkRequest;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.ReworkRecord;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.ReworkRecordMapper;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.DispatchService;
import com.property.repair.service.ReworkService;
import com.property.repair.statemachine.OrderStateMachine;
import com.property.repair.statemachine.StateChangeListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReworkServiceImpl implements ReworkService {

    private final RepairOrderMapper repairOrderMapper;
    private final ReworkRecordMapper reworkRecordMapper;
    private final OrderStateMachine orderStateMachine;
    private final StateChangeListener stateChangeListener;
    private final DispatchService dispatchService;

    public ReworkServiceImpl(RepairOrderMapper repairOrderMapper,
                             ReworkRecordMapper reworkRecordMapper,
                             OrderStateMachine orderStateMachine,
                             StateChangeListener stateChangeListener,
                             DispatchService dispatchService) {
        this.repairOrderMapper = repairOrderMapper;
        this.reworkRecordMapper = reworkRecordMapper;
        this.orderStateMachine = orderStateMachine;
        this.stateChangeListener = stateChangeListener;
        this.dispatchService = dispatchService;
    }

    @Override
    @Transactional
    public Result<?> requestRework(Long orderId, ReworkRequest request) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.EVALUATED) {
            throw new BusinessException("当前状态不支持返工申请");
        }

        if (order.getReworkCount() >= RepairConstants.MAX_REWORK_COUNT) {
            throw new BusinessException("返工次数已达上限(" + RepairConstants.MAX_REWORK_COUNT + "次)");
        }

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = orderStateMachine.fire(order, OrderEvent.REQUEST_REWORK,
                currentUser.getUserId(), currentUser.getRole());

        ReworkRecord reworkRecord = new ReworkRecord();
        reworkRecord.setOrderId(orderId);
        reworkRecord.setInitiatorId(currentUser.getUserId());
        reworkRecord.setReason(request.getReason());
        reworkRecord.setFromStatus(fromStatus.name());
        reworkRecord.setPreviousWorkerId(order.getCurrentWorkerId());
        reworkRecordMapper.insert(reworkRecord);

        order.setStatus(toStatus);
        order.setReworkCount(order.getReworkCount() + 1);
        int rows = repairOrderMapper.updateById(order);
        if (rows == 0) {
            throw new BusinessException("操作失败，数据已被修改，请刷新后重试");
        }

        stateChangeListener.onStateChange(order, fromStatus, toStatus,
                currentUser.getUserId(), "发起返工: " + request.getReason());

        try {
            dispatchService.autoDispatch(order);
        } catch (Exception e) {
            // Auto dispatch failure is non-critical
        }

        return Result.success("返工申请已提交");
    }
}
