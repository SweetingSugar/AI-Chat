package com.example.aichat.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<SysUser> {
    @Select("select * from sys_user where username = #{username} and deleted = 0 limit 1")
    SysUser findByUsername(String username);
}
