alter table data_export_log add column data_scope varchar(120) not null default 'LEGACY';
alter table data_export_log add column filter_summary varchar(500) null;

create index idx_data_export_scope on data_export_log(data_scope, created_at);
