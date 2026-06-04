alter table work_order add column priority varchar(24) not null default 'NORMAL';
alter table work_order add column due_at datetime null;
alter table work_order add column satisfaction_score int null;
alter table work_order add column satisfaction_comment varchar(500) null;
alter table work_order add column evaluated_at datetime null;

create table work_order_sla_rule (
    id bigint primary key auto_increment,
    order_type varchar(32) not null,
    priority varchar(24) not null,
    response_hours int not null,
    status varchar(24) not null,
    created_at datetime not null,
    unique key uk_work_order_sla(order_type, priority)
);

insert into work_order_sla_rule(order_type, priority, response_hours, status, created_at) values
('REPAIR', 'NORMAL', 48, 'ACTIVE', now()),
('REPAIR', 'URGENT', 24, 'ACTIVE', now()),
('COMPLAINT', 'NORMAL', 72, 'ACTIVE', now()),
('COMPLAINT', 'URGENT', 48, 'ACTIVE', now());

update work_order
set due_at = created_at
where due_at is null;
