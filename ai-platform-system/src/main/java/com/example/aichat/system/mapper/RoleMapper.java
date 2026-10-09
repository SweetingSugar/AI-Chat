package com.example.aichat.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.system.entity.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {
    @Select("select r.role_code from sys_role r join sys_user_role ur on ur.role_id = r.id where ur.user_id = #{userId} and r.status = 1")
    List<String> findRoleCodesByUserId(Long userId);
}
