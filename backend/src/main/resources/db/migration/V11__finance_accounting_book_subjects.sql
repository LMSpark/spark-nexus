create table accounting_book (
  id bigint primary key auto_increment,
  community_id bigint not null,
  book_name varchar(128) not null,
  enabled_month varchar(16) not null,
  base_currency varchar(16) not null,
  status varchar(24) not null,
  created_at datetime not null
);

create table finance_subject (
  id bigint primary key auto_increment,
  subject_code varchar(32) not null,
  subject_name varchar(128) not null,
  subject_type varchar(32) not null,
  direction varchar(16) not null,
  status varchar(24) not null
);

create table subject_initial_balance (
  id bigint primary key auto_increment,
  community_id bigint not null,
  subject_code varchar(32) not null,
  opening_month varchar(16) not null,
  direction varchar(16) not null,
  amount decimal(14,2) not null,
  created_at datetime not null
);

create table finance_voucher_line (
  id bigint primary key auto_increment,
  voucher_id bigint not null,
  community_id bigint not null,
  subject_code varchar(32) not null,
  subject_name varchar(128) not null,
  direction varchar(16) not null,
  amount decimal(14,2) not null,
  booked_at datetime not null
);

create index idx_finance_voucher_line_voucher on finance_voucher_line(voucher_id);
create index idx_finance_voucher_line_community_subject on finance_voucher_line(community_id, subject_code);

insert into accounting_book(community_id, book_name, enabled_month, base_currency, status, created_at) values
(1, '鼎盛阳光一期公共收益账套', '2026-01', 'CNY', 'ACTIVE', now()),
(2, '鼎盛阳光二期公共收益账套', '2026-01', 'CNY', 'ACTIVE', now()),
(3, '星河湾花园公共收益账套', '2026-01', 'CNY', 'ACTIVE', now());

insert into finance_subject(subject_code, subject_name, subject_type, direction, status) values
('1002', '银行存款-公共收益专户', 'ASSET', 'DEBIT', 'ACTIVE'),
('4001', '公共收益收入', 'INCOME', 'CREDIT', 'ACTIVE'),
('5001', '公共设施维护支出', 'EXPENSE', 'DEBIT', 'ACTIVE'),
('2202', '应付账款-工程服务', 'LIABILITY', 'CREDIT', 'ACTIVE');

insert into subject_initial_balance(community_id, subject_code, opening_month, direction, amount, created_at) values
(1, '1002', '2026-01', 'DEBIT', 80000.00, now()),
(1, '4001', '2026-01', 'CREDIT', 0.00, now()),
(1, '5001', '2026-01', 'DEBIT', 0.00, now()),
(2, '1002', '2026-01', 'DEBIT', 52000.00, now()),
(3, '1002', '2026-01', 'DEBIT', 38000.00, now());

insert into finance_voucher_line(voucher_id, community_id, subject_code, subject_name, direction, amount, booked_at)
select id, community_id,
       case when debit_subject like '%银行存款%' then '1002'
            when debit_subject like '%公共设施维护%' then '5001'
            else '5001' end,
       debit_subject, 'DEBIT', amount, booked_at
from finance_voucher
where status = 'BOOKED' and booked_at is not null;

insert into finance_voucher_line(voucher_id, community_id, subject_code, subject_name, direction, amount, booked_at)
select id, community_id,
       case when credit_subject like '%银行存款%' then '1002'
            when credit_subject like '%公共收益收入%' then '4001'
            when credit_subject like '%应付账款%' then '2202'
            else '4001' end,
       credit_subject, 'CREDIT', amount, booked_at
from finance_voucher
where status = 'BOOKED' and booked_at is not null;
