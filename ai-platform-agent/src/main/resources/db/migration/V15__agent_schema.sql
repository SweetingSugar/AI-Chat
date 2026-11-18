create table if not exists agent_definition (
    id bigint not null auto_increment primary key comment 'Agent 主键 ID',
    user_id bigint not null comment '所属用户 ID',
    name varchar(100) not null comment 'Agent 名称',
    description varchar(1000) null comment 'Agent 描述',
    system_prompt text not null comment 'Agent 系统提示词',
    model_id bigint not null comment '聊天模型 ID',
    status tinyint not null default 1 comment '启用状态：0 禁用，1 启用',
    deleted tinyint not null default 0 comment '逻辑删除标记：0 未删除，1 已删除',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_agent_user_name_deleted (user_id, name, deleted),
    key idx_agent_user_status (user_id, status, deleted, update_time),
    key idx_agent_model (model_id, deleted)
) comment = '用户 Agent 配置表';

create table if not exists agent_knowledge_base (
    id bigint not null auto_increment primary key comment '绑定主键 ID',
    agent_id bigint not null comment 'Agent ID',
    knowledge_base_id bigint not null comment '知识库 ID',
    create_time datetime not null default current_timestamp comment '创建时间',
    unique key uk_agent_knowledge_base (agent_id, knowledge_base_id),
    key idx_agent_knowledge_agent (agent_id),
    key idx_agent_knowledge_base_id (knowledge_base_id)
) comment = 'Agent 知识库绑定';

create table if not exists agent_tool (
    id bigint not null auto_increment primary key comment '绑定主键 ID',
    agent_id bigint not null comment 'Agent ID',
    tool_code varchar(100) not null comment '全局工具编码',
    create_time datetime not null default current_timestamp comment '创建时间',
    unique key uk_agent_tool (agent_id, tool_code),
    key idx_agent_tool_agent (agent_id),
    key idx_agent_tool_code (tool_code)
) comment = 'Agent 工具绑定';
