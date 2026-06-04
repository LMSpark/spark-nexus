create table external_callback_receipt (
    id bigint primary key auto_increment,
    adapter_code varchar(40) not null,
    event_type varchar(64) not null,
    business_no varchar(96) not null,
    signature_status varchar(24) not null,
    http_status int not null,
    request_digest varchar(1000) not null,
    response_summary varchar(500) not null,
    trace_no varchar(96) not null,
    received_at datetime not null
);

create index idx_external_callback_adapter on external_callback_receipt(adapter_code, received_at);
create index idx_external_callback_business on external_callback_receipt(business_no, event_type);
