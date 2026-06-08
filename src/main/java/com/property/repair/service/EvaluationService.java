package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.dto.request.EvaluationRequest;
import com.property.repair.entity.Evaluation;

import java.util.List;

public interface EvaluationService {

    Result<?> evaluate(Long orderId, EvaluationRequest request);

    Result<List<Evaluation>> getByWorkerId(Long workerId);
}
