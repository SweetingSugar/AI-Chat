package com.example.aichat.system.vo;

public record SessionVO(boolean loggedIn, Long userId, long tokenTimeout) { }
