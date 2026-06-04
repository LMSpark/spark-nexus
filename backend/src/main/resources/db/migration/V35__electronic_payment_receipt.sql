create table electronic_payment_receipt (
    id bigint primary key auto_increment,
    receipt_no varchar(80) not null unique,
    bill_id bigint not null,
    payment_order_id bigint not null,
    community_id bigint not null,
    room_no varchar(50) not null,
    payer_name varchar(100) not null,
    bill_type varchar(60) not null,
    period varchar(30) not null,
    pay_channel varchar(40) not null,
    amount decimal(18,2) not null,
    status varchar(32) not null,
    file_url varchar(300) not null,
    checksum varchar(128) not null,
    issued_by varchar(64) not null,
    issued_at datetime not null
);

create unique index uk_electronic_payment_receipt_order on electronic_payment_receipt(payment_order_id);
create index idx_electronic_payment_receipt_community on electronic_payment_receipt(community_id, issued_at);
create index idx_electronic_payment_receipt_bill on electronic_payment_receipt(bill_id);
