create table approval_rule (
  id bigint primary key auto_increment,
  community_id bigint not null,
  expense_threshold decimal(14,2) not null,
  vote_threshold decimal(14,2) not null,
  approval_timeout_hours int not null,
  vote_ratio decimal(8,2) not null,
  status varchar(24) not null,
  created_at datetime not null
);

alter table expense_order add column source_account_type varchar(32) not null default 'PUBLIC_REVENUE';
alter table expense_order add column need_vote varchar(8) not null default 'NO';

insert into approval_rule(community_id, expense_threshold, vote_threshold, approval_timeout_hours, vote_ratio, status, created_at) values
(1, 50000.00, 100000.00, 24, 66.67, 'ACTIVE', now()),
(2, 30000.00, 80000.00, 24, 66.67, 'ACTIVE', now()),
(3, 20000.00, 50000.00, 24, 66.67, 'ACTIVE', now());
