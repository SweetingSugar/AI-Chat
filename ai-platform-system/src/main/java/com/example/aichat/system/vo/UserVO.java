package com.example.aichat.system.vo;

import com.example.aichat.system.entity.SysUser;
import java.time.LocalDateTime;
import java.util.List;

/** Password hashes are deliberately absent from API responses. */
public record UserVO(Long id, String username, String nickname, String email, String phone,
                     String avatar, Integer status, LocalDateTime lastLoginTime, LocalDateTime createTime,
                     List<String> roleCodes, List<String> permissionCodes) {
    public static UserVO from(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getPhone(),
                user.getAvatar(), user.getStatus(), user.getLastLoginTime(), user.getCreateTime(),
                List.of(), List.of());
    }

    public UserVO withCodes(List<String> roleCodes, List<String> permissionCodes) {
        return new UserVO(id, username, nickname, email, phone, avatar, status, lastLoginTime, createTime,
                roleCodes, permissionCodes);
    }
}
