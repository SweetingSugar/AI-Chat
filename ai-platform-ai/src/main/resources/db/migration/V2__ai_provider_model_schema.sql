create table if not exists ai_provider (
    id bigint not null auto_increment primary key,
    provider_code varchar(50) not null,
    provider_name varchar(100) not null,
    provider_type varchar(32) not null,
    base_url varchar(500) not null,
    api_key_ciphertext text not null,
    credential_version int not null default 1,
    connect_timeout int not null default 10,
    read_timeout int not null default 60,
    status tinyint not null default 1,
    sort int not null default 0,
    remark varchar(500) null,
    create_by bigint null,
    create_time datetime not null default current_timestamp,
    update_by bigint null,
    update_time datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_ai_provider_code (provider_code),
    key idx_ai_provider_status_sort (status, sort)
);

create table if not exists ai_model (
    id bigint not null auto_increment primary key,
    provider_id bigint not null,
    model_code varchar(100) not null,
    model_name varchar(100) not null,
    model_type varchar(32) not null,
    context_window int not null,
    max_output_tokens int not null,
    temperature decimal(5,4) null,
    top_p decimal(5,4) null,
    support_stream tinyint not null default 1,
    is_default tinyint not null default 0,
    config_version int not null default 1,
    status tinyint not null default 1,
    sort int not null default 0,
    remark varchar(500) null,
    create_by bigint null,
    create_time datetime not null default current_timestamp,
    update_by bigint null,
    update_time datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_ai_model_provider_code (provider_id, model_code),
    key idx_ai_model_provider_status_sort (provider_id, status, sort),
    key idx_ai_model_default_status (is_default, status)
);

insert ignore into sys_permission (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'AI provider read', 'ai:provider:read', 3, 0, 1),
    (0, 'AI provider create', 'ai:provider:create', 3, 1, 1),
    (0, 'AI provider update', 'ai:provider:update', 3, 2, 1),
    (0, 'AI provider delete', 'ai:provider:delete', 3, 3, 1),
    (0, 'AI model read', 'ai:model:read', 3, 4, 1),
    (0, 'AI model create', 'ai:model:create', 3, 5, 1),
    (0, 'AI model update', 'ai:model:update', 3, 6, 1),
    (0, 'AI model delete', 'ai:model:delete', 3, 7, 1);
