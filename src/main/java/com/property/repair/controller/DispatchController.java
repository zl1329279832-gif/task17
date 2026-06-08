package com.property.repair.controller;

import com.property.repair.common.result.Result;
import com.property.repair.dto.request.DispatchRequest;
import com.property.repair.dto.response.WorkerResponse;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.DispatchService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/candidates/{orderId}")
    public Result<List<WorkerResponse>> getCandidates(@PathVariable Long orderId) {
        List<WorkerResponse> candidates = dispatchService.getCandidates(orderId);
        return Result.success(candidates);
    }

    @PostMapping("/manual")
    public Result<?> manualDispatch(@Valid @RequestBody DispatchRequest request) {
        dispatchService.manualDispatch(request);
        return Result.success("派单成功");
    }

    @PostMapping("/reassign")
    public Result<?> reassign(@RequestParam Long orderId,
                              @RequestParam Long workerId,
                              @RequestParam(required = false) String reason) {
        Long operatorId = SecurityUtils.getCurrentUserId();
        dispatchService.reassign(orderId, workerId, operatorId, reason);
        return Result.success("重新派单成功");
    }
}
