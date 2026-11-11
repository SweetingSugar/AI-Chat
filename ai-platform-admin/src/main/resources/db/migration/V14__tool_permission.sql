-- Tool management, MCP server, execution, and audit permissions.
insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'Tool read', 'tool:read', 3, 300, 1),
    (0, 'Tool create', 'tool:create', 3, 301, 1),
    (0, 'Tool update', 'tool:update', 3, 302, 1),
    (0, 'Tool delete', 'tool:delete', 3, 303, 1),
    (0, 'Tool execute', 'tool:execute', 3, 304, 1),
    (0, 'MCP server read', 'tool:mcp-server:read', 3, 310, 1),
    (0, 'MCP server create', 'tool:mcp-server:create', 3, 311, 1),
    (0, 'MCP server update', 'tool:mcp-server:update', 3, 312, 1),
    (0, 'MCP server delete', 'tool:mcp-server:delete', 3, 313, 1),
    (0, 'MCP server discover', 'tool:mcp-server:discover', 3, 314, 1),
    (0, 'Tool invocation log read', 'tool:invocation:read', 3, 320, 1);
