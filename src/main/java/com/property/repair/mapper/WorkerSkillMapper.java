package com.property.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.repair.entity.WorkerSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkerSkillMapper extends BaseMapper<WorkerSkill> {

    List<WorkerSkill> selectByWorkerId(@Param("workerId") Long workerId);

    List<WorkerSkill> selectByCommunityAndCategory(@Param("communityId") Long communityId, @Param("categoryId") Long categoryId);
}
