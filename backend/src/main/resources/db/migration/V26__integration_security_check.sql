create table integration_security_check (
    id bigint primary key auto_increment,
    adapter_code varchar(40) not null,
    check_item varchar(80) not null,
    check_result varchar(24) not null,
    risk_level varchar(24) not null,
    evidence varchar(500) not null,
    checked_at datetime not null
);

create index idx_integration_security_check_adapter on integration_security_check(adapter_code, checked_at);
