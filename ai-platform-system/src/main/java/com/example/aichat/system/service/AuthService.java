package com.example.aichat.system.service;

import com.example.aichat.system.dto.LoginDTO;
import com.example.aichat.system.vo.LoginVO;
import com.example.aichat.system.vo.SessionVO;
import com.example.aichat.system.vo.UserVO;

public interface AuthService {
    LoginVO login(LoginDTO request, String clientIp);

    void logout();

    void logoutAll();

    UserVO me();

    SessionVO session();
}
