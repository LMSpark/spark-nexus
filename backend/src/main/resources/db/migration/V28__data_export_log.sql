create table data_export_log (
    id bigint primary key auto_increment,
    actor varchar(64) not null,
    role_code varchar(32) not null,
    tenant_id bigint not null,
    community_id bigint null,
    export_module varchar(64) not null,
    target_type varchar(64) not null,
    target_id bigint not null,
    file_name varchar(160) not null,
    row_count int not null,
    status varchar(24) not null,
    created_at datetime not null
);

create index idx_data_export_actor on data_export_log(actor, created_at);
create index idx_data_export_module on data_export_log(export_module, created_at);
