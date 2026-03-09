package com.timemap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timemap.model.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
