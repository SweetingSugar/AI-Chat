package com.example.aichat.system.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.system.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("select id, username, password_hash as passwordHash, status from sys_user where username = #{username} and deleted = 0")
    User findByUsername(String username);
}
