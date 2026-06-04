create table fee_standard (
  id bigint primary key auto_increment,
  community_id bigint not null,
  fee_type varchar(32) not null,
  billing_mode varchar(32) not null,
  unit_price decimal(12,2) not null,
  cycle varchar(24) not null,
  effective_from date not null,
  status varchar(24) not null,
  created_at datetime not null
);

create table bill_generation_batch (
  id bigint primary key auto_increment,
  community_id bigint not null,
  period varchar(16) not null,
  generated_count int not null,
  total_amount decimal(14,2) not null,
  operator varchar(64) not null,
  created_at datetime not null
);

insert into fee_standard(community_id, fee_type, billing_mode, unit_price, cycle, effective_from, status, created_at) values
(1, '物业费', 'AREA', 3.00, 'MONTHLY', '2026-01-01', 'ACTIVE', now()),
(1, '停车费', 'FIXED', 280.00, 'MONTHLY', '2026-01-01', 'ACTIVE', now()),
(2, '物业费', 'AREA', 2.80, 'MONTHLY', '2026-01-01', 'ACTIVE', now()),
(3, '物业费', 'AREA', 2.60, 'MONTHLY', '2026-01-01', 'ACTIVE', now());
