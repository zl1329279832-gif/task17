package com.property.repair.controller;

import com.property.repair.common.result.Result;
import com.property.repair.entity.Evaluation;
import com.property.repair.service.EvaluationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/worker/{workerId}")
    public Result<List<Evaluation>> getWorkerEvaluations(@PathVariable Long workerId) {
        return evaluationService.getByWorkerId(workerId);
    }
}
