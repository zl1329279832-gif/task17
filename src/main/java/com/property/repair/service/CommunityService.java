package com.property.repair.service;

import com.property.repair.common.result.Result;
import com.property.repair.entity.Building;
import com.property.repair.entity.Community;

import java.util.List;

public interface CommunityService {

    Result<List<Community>> list();

    Result<Community> getById(Long id);

    Result<List<Community>> getBySupervisorId(Long supervisorId);

    Result<List<Building>> getBuildingsByCommunityId(Long communityId);
}
