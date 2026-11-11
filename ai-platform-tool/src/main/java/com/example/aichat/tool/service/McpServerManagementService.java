package com.example.aichat.tool.service;

import com.example.aichat.tool.dto.management.McpServerManagementView;
import com.example.aichat.tool.dto.management.McpServerStatusRequest;
import com.example.aichat.tool.dto.management.McpServerUpsertRequest;
import com.example.aichat.tool.entity.ToolMcpServer;

import java.util.List;

public interface McpServerManagementService {
    List<McpServerManagementView> listManagement();

    McpServerManagementView getManagementRequired(Long serverId);

    ToolMcpServer getRequired(Long serverId);

    McpServerManagementView create(McpServerUpsertRequest request);

    McpServerManagementView update(Long serverId, McpServerUpsertRequest request);

    void delete(Long serverId);

    McpServerManagementView updateStatus(Long serverId, McpServerStatusRequest request);
}
