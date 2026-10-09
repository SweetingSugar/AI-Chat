package com.example.aichat.system.vo;

import com.example.aichat.system.entity.SysUser;
import java.time.LocalDateTime;

/** Password hashes are deliberately absent from API responses. */
public record UserVO(Long id, String username, String nickname, String email, String phone,
                     String avatar, Integer status, LocalDateTime lastLoginTime, LocalDateTime createTime) {
    public static UserVO from(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getPhone(),
                user.getAvatar(), user.getStatus(), user.getLastLoginTime(), user.getCreateTime());
    }
}
