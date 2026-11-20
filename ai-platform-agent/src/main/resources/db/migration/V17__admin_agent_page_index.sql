alter table agent_definition
    add index idx_agent_admin_page (deleted, update_time, id);
