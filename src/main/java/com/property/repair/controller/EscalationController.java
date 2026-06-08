package com.property.repair.controller;

import com.property.repair.common.result.Result;
import com.property.repair.entity.TimeoutEscalation;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.TimeoutEscalationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escalations")
public class EscalationController {

    private final TimeoutEscalationService timeoutEscalationService;

    public EscalationController(TimeoutEscalationService timeoutEscalationService) {
        this.timeoutEscalationService = timeoutEscalationService;
    }

    @GetMapping
    public Result<List<TimeoutEscalation>> getPendingEscalations() {
        Long supervisorId = SecurityUtils.getCurrentUserId();
        return timeoutEscalationService.getPendingEscalations(supervisorId);
    }

    @PostMapping("/{id}/resolve")
    public Result<?> resolveEscalation(@PathVariable Long id,
                                        @RequestParam Long newWorkerId) {
        return timeoutEscalationService.resolveEscalation(id, newWorkerId);
    }
}
