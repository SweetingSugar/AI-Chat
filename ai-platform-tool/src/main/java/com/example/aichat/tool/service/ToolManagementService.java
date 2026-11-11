package com.example.aichat.tool.service;

import com.example.aichat.tool.dto.management.ToolManagementView;
import com.example.aichat.tool.dto.management.ToolStatusRequest;
import com.example.aichat.tool.dto.management.ToolUpsertRequest;
import com.example.aichat.tool.entity.ToolDefinition;

import java.util.List;

public interface ToolManagementService {
    List<ToolManagementView> listManagement();

    ToolManagementView getManagementRequired(Long toolId);

    ToolDefinition getRequired(Long toolId);

    ToolManagementView create(ToolUpsertRequest request);

    ToolManagementView update(Long toolId, ToolUpsertRequest request);

    void delete(Long toolId);

    ToolManagementView updateStatus(Long toolId, ToolStatusRequest request);
}
