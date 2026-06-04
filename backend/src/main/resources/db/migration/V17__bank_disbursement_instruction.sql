create table bank_disbursement_instruction (
  id bigint primary key auto_increment,
  expense_id bigint not null,
  community_id bigint not null,
  fund_account_id bigint null,
  instruction_no varchar(64) not null unique,
  payee_name varchar(128) not null,
  amount decimal(14,2) not null,
  purpose varchar(255) not null,
  status varchar(24) not null,
  bank_trace_no varchar(80) null,
  created_at datetime not null,
  processed_by varchar(64) null,
  processed_at datetime null,
  remark varchar(500) null
);

create index idx_bank_disbursement_community on bank_disbursement_instruction(community_id, status, created_at);
create index idx_bank_disbursement_expense on bank_disbursement_instruction(expense_id);
