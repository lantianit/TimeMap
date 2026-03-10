package com.timemap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timemap.model.entity.UserViolation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserViolationMapper extends BaseMapper<UserViolation> {
}
