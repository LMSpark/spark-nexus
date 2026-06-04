create table arrears_publication (
  id bigint primary key auto_increment,
  community_id bigint not null,
  period varchar(16) not null,
  title varchar(128) not null,
  total_households int not null,
  arrears_count int not null,
  arrears_amount decimal(14,2) not null,
  status varchar(24) not null,
  published_by varchar(64) not null,
  published_at datetime not null
);

create table arrears_publication_item (
  id bigint primary key auto_increment,
  publication_id bigint not null,
  bill_id bigint not null,
  room_no_mask varchar(40) not null,
  bill_type varchar(32) not null,
  period varchar(16) not null,
  amount decimal(14,2) not null,
  paid_amount decimal(14,2) not null,
  arrears_amount decimal(14,2) not null,
  due_date date not null
);

create index idx_arrears_publication_community on arrears_publication(community_id, published_at);
create index idx_arrears_publication_item_publication on arrears_publication_item(publication_id);
