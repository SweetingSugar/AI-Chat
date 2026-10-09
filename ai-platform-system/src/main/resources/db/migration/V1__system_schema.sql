create table if not exists sys_user (
    id bigint not null auto_increment primary key,
    username varchar(50) not null,
    password varchar(255) not null,
    nickname varchar(50) null,
    email varchar(100) null,
    phone varchar(20) null,
    avatar varchar(500) null,
    status tinyint not null default 1,
    last_login_time datetime null,
    last_login_ip varchar(45) null,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_username (username),
    unique key uk_email (email),
    unique key uk_phone (phone)
);

create table if not exists sys_role (
    id bigint not null auto_increment primary key,
    role_name varchar(50) not null,
    role_code varchar(50) not null,
    description varchar(255) null,
    sort int not null default 0,
    status tinyint not null default 1,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    unique key uk_role_code (role_code)
);

create table if not exists sys_permission (
    id bigint not null auto_increment primary key,
    parent_id bigint not null default 0,
    permission_name varchar(100) not null,
    permission_code varchar(100) null,
    permission_type tinyint not null,
    path varchar(255) null,
    component varchar(255) null,
    icon varchar(100) null,
    sort int not null default 0,
    status tinyint not null default 1,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    unique key uk_permission_code (permission_code),
    key idx_parent_id (parent_id)
);

create table if not exists sys_user_role (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    role_id bigint not null,
    unique key uk_user_role (user_id, role_id),
    key idx_role_id (role_id)
);

create table if not exists sys_role_permission (
    id bigint not null auto_increment primary key,
    role_id bigint not null,
    permission_id bigint not null,
    unique key uk_role_permission (role_id, permission_id),
    key idx_permission_id (permission_id)
);

create table if not exists sys_login_log (
    id bigint not null auto_increment primary key,
    user_id bigint null,
    username varchar(50) null,
    ip varchar(45) null,
    user_agent varchar(500) null,
    login_status tinyint not null,
    message varchar(255) null,
    login_time datetime not null default current_timestamp,
    key idx_user_time (user_id, login_time)
);

create table if not exists sys_operation_log (
    id bigint not null auto_increment primary key,
    user_id bigint null,
    username varchar(50) null,
    module varchar(100) null,
    operation varchar(100) null,
    request_method varchar(10) null,
    request_uri varchar(500) null,
    ip varchar(45) null,
    status tinyint not null,
    message varchar(500) null,
    create_time datetime not null default current_timestamp,
    key idx_user_time (user_id, create_time)
);
