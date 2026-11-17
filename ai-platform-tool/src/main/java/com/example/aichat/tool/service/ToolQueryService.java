package com.example.aichat.tool.service;

import com.example.aichat.tool.dto.query.AvailableToolView;

import java.util.List;

public interface ToolQueryService {
    List<AvailableToolView> listAvailable();
}
