create table data_exchange_package (
    id bigint primary key auto_increment,
    package_no varchar(80) not null unique,
    target_party varchar(64) not null,
    community_id bigint null,
    data_domains varchar(500) not null,
    record_count int not null,
    checksum varchar(128) not null,
    status varchar(32) not null,
    created_by varchar(64) not null,
    created_at datetime not null
);

create index idx_data_exchange_package_target on data_exchange_package(target_party, created_at);
create index idx_data_exchange_package_community on data_exchange_package(community_id, created_at);
