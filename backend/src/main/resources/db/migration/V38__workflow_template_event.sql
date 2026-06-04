create table workflow_template (
    id bigint primary key auto_increment,
    template_code varchar(64) not null unique,
    template_name varchar(120) not null,
    business_type varchar(60) not null,
    status varchar(32) not null,
    created_at datetime not null
);

create table workflow_template_node (
    id bigint primary key auto_increment,
    template_code varchar(64) not null,
    node_code varchar(64) not null,
    node_name varchar(120) not null,
    role_code varchar(64) not null,
    sort_no int not null,
    condition_code varchar(64) not null,
    timeout_hours int not null,
    status varchar(32) not null,
    created_at datetime not null,
    unique(template_code, node_code)
);

create table workflow_event (
    id bigint primary key auto_increment,
    business_type varchar(60) not null,
    business_id bigint not null,
    template_code varchar(64) not null,
    node_code varchar(64) not null,
    event_type varchar(64) not null,
    operator varchar(64) not null,
    event_summary varchar(500) not null,
    created_at datetime not null
);

create index idx_workflow_template_node_template on workflow_template_node(template_code, sort_no);
create index idx_workflow_event_business on workflow_event(business_type, business_id, created_at);

insert into workflow_template(template_code, template_name, business_type, status, created_at)
values('PUBLIC_EXPENSE', '公共收益支出审批流程', 'EXPENSE', 'ACTIVE', now());

insert into workflow_template_node(template_code, node_code, node_name, role_code, sort_no, condition_code, timeout_hours, status, created_at) values
('PUBLIC_EXPENSE', 'SUBMIT', '提交申请', 'COMMITTEE', 1, 'ALWAYS', 0, 'ACTIVE', now()),
('PUBLIC_EXPENSE', 'COMMITTEE_REVIEW', '业委会审批', 'COMMITTEE', 2, 'ALWAYS', 24, 'ACTIVE', now()),
('PUBLIC_EXPENSE', 'STREET_REVIEW', '社区/街道审批', 'STREET', 3, 'COMMUNITY_APPROVAL', 24, 'ACTIVE', now()),
('PUBLIC_EXPENSE', 'OWNER_VOTE', '业主大会表决', 'OWNER', 4, 'OWNER_VOTE', 72, 'ACTIVE', now()),
('PUBLIC_EXPENSE', 'BANK_PAYMENT', '银行放款', 'BANK', 5, 'ALWAYS', 24, 'ACTIVE', now());
