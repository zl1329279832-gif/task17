package com.property.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.repair.entity.Community;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommunityMapper extends BaseMapper<Community> {

    List<Community> selectBySupervisorId(@Param("supervisorId") Long supervisorId);
}
