alter table reconciliation_detail add column handled_status varchar(32) not null default 'OPEN';
alter table reconciliation_detail add column handled_by varchar(64) null;
alter table reconciliation_detail add column handled_at datetime null;
alter table reconciliation_detail add column handle_remark varchar(255) null;

update reconciliation_detail
set handled_status = case when status = 'MATCHED' then 'AUTO_CLOSED' else 'OPEN' end
where handled_status = 'OPEN';
