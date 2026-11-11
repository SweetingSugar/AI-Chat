package com.example.aichat.tool.executor;

import com.example.aichat.tool.entity.ToolMcpServer;
import io.modelcontextprotocol.client.McpSyncClient;

public interface McpClientFactory {
    McpSyncClient create(ToolMcpServer server);
}
