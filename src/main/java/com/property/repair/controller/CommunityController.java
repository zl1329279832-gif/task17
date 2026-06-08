package com.property.repair.controller;

import com.property.repair.common.result.Result;
import com.property.repair.entity.Building;
import com.property.repair.entity.Community;
import com.property.repair.service.CommunityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping
    public Result<List<Community>> list() {
        return communityService.list();
    }

    @GetMapping("/{id}")
    public Result<Community> getById(@PathVariable Long id) {
        return communityService.getById(id);
    }

    @GetMapping("/{id}/buildings")
    public Result<List<Building>> getBuildingsByCommunityId(@PathVariable Long id) {
        return communityService.getBuildingsByCommunityId(id);
    }
}
