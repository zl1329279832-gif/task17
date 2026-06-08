package com.property.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.Result;
import com.property.repair.dto.DispatchCandidate;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.TimeoutEscalation;
import com.property.repair.dto.EscalationHandleRequest;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.TimeoutEscalationMapper;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.DispatchStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class AdminController {

    private final DispatchStrategy dispatchStrategy;
    private final RepairOrderMapper orderMapper;
    private final TimeoutEscalationMapper escalationMapper;

    /**
     * Preview auto-dispatch candidates for an order (debugging tool).
     */
    @GetMapping("/dispatch/candidates/{orderId}")
    public Result<List<DispatchCandidate>> getDispatchCandidates(@PathVariable Long orderId) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.fail("Order not found");
        }
        return Result.ok(dispatchStrategy.getCandidates(order));
    }

    /**
     * Get unhandled timeout escalations for the current supervisor.
     */
    @GetMapping("/escalations")
    public Result<List<TimeoutEscalation>> getUnhandledEscalations() {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentRole();

        LambdaQueryWrapper<TimeoutEscalation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TimeoutEscalation::getHandled, 0);

        // Supervisors only see their own community escalations
        if ("SUPERVISOR".equals(role)) {
            wrapper.eq(TimeoutEscalation::getEscalatedTo, userId);
        }

        wrapper.orderByAsc(TimeoutEscalation::getCreatedAt);
        return Result.ok(escalationMapper.selectList(wrapper));
    }

    /**
     * Handle a timeout escalation.
     */
    @PostMapping("/escalations/{escalationId}/handle")
    public Result<Void> handleEscalation(@PathVariable Long escalationId,
                                          @RequestBody EscalationHandleRequest request) {
        TimeoutEscalation esc = escalationMapper.selectById(escalationId);
        if (esc == null) {
            return Result.fail("Escalation not found");
        }

        esc.setHandled(1);
        esc.setHandledAt(LocalDateTime.now());
        esc.setHandledBy(SecurityUtils.getCurrentUserId());
        esc.setHandleRemark(request.getRemark());
        escalationMapper.updateById(esc);

        return Result.ok();
    }
}
