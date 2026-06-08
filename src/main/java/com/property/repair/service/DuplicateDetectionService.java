package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.dto.response.DuplicateCheckResponse;

public interface DuplicateDetectionService {

    DuplicateCheckResponse checkDuplicate(Long communityId, Long buildingId, Long categoryId);

    void linkDuplicates(Long orderId, String duplicateGroup);
}
