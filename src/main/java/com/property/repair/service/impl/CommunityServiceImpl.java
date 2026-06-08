package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.Result;
import com.property.repair.entity.Building;
import com.property.repair.entity.Community;
import com.property.repair.mapper.BuildingMapper;
import com.property.repair.mapper.CommunityMapper;
import com.property.repair.service.CommunityService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommunityServiceImpl implements CommunityService {

    private final CommunityMapper communityMapper;
    private final BuildingMapper buildingMapper;

    public CommunityServiceImpl(CommunityMapper communityMapper, BuildingMapper buildingMapper) {
        this.communityMapper = communityMapper;
        this.buildingMapper = buildingMapper;
    }

    @Override
    public Result<List<Community>> list() {
        LambdaQueryWrapper<Community> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Community::getStatus, 1)
                .orderByAsc(Community::getName);
        List<Community> communities = communityMapper.selectList(wrapper);
        return Result.success(communities);
    }

    @Override
    public Result<Community> getById(Long id) {
        Community community = communityMapper.selectById(id);
        if (community == null) {
            throw new BusinessException("小区不存在");
        }
        return Result.success(community);
    }

    @Override
    public Result<List<Community>> getBySupervisorId(Long supervisorId) {
        List<Community> communities = communityMapper.selectBySupervisorId(supervisorId);
        return Result.success(communities);
    }

    @Override
    public Result<List<Building>> getBuildingsByCommunityId(Long communityId) {
        Community community = communityMapper.selectById(communityId);
        if (community == null) {
            throw new BusinessException("小区不存在");
        }

        LambdaQueryWrapper<Building> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Building::getCommunityId, communityId)
                .orderByAsc(Building::getName);
        List<Building> buildings = buildingMapper.selectList(wrapper);
        return Result.success(buildings);
    }
}
