package com.example.aichat.system.service;

import com.example.aichat.system.dto.RegisterDTO;
import com.example.aichat.system.dto.UserUpdateDTO;
import com.example.aichat.system.entity.SysUser;
import java.util.Collection;
import java.util.List;

public interface UserService {
    SysUser register(RegisterDTO request);
    SysUser getRequired(Long id);
    List<SysUser> list();
    SysUser update(Long id, UserUpdateDTO request);
    void updateStatus(Long id, Integer status);
    void assignRoles(Long userId, Collection<Long> roleIds);
    void changePassword(Long userId, String oldPassword, String newPassword);
}
