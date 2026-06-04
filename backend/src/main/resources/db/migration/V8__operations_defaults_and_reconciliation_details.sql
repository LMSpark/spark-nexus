create table reconciliation_detail (
  id bigint primary key auto_increment,
  reconciliation_id bigint not null,
  source_type varchar(32) not null,
  source_id bigint,
  order_no varchar(64),
  trace_no varchar(64),
  amount decimal(14,2) not null,
  status varchar(32) not null,
  description varchar(255) not null,
  created_at datetime not null
);

alter table registration_application add column owner_user_id bigint null;

update registration_application set owner_user_id = null where owner_user_id is null;
