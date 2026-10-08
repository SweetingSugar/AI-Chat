create table if not exists sys_user (
    id bigint primary key,
    username varchar(64) not null unique,
    password_hash varchar(100) not null,
    status varchar(16) not null,
    deleted tinyint not null default 0,
    created_at datetime(3) not null default current_timestamp(3),
    updated_at datetime(3) not null default current_timestamp(3) on update current_timestamp(3)
);
create table if not exists sys_role (
    id bigint primary key,
    code varchar(64) not null unique,
    name varchar(64) not null
);
create table if not exists sys_permission (
    id bigint primary key,
    code varchar(128) not null unique,
    name varchar(64) not null
);
create table if not exists sys_user_role (
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id)
);
create table if not exists sys_role_permission (
    role_id bigint not null,
    permission_id bigint not null,
    primary key (role_id, permission_id)
);
