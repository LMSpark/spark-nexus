create table payment_refund_order (
    id bigint primary key auto_increment,
    payment_order_id bigint not null,
    bill_id bigint not null,
    community_id bigint not null,
    refund_no varchar(80) not null unique,
    pay_channel varchar(32) not null,
    amount decimal(14,2) not null,
    reason varchar(255) not null,
    status varchar(32) not null,
    provider_refund_no varchar(96) null,
    processed_at datetime null,
    created_at datetime not null
);

create index idx_payment_refund_bill on payment_refund_order(bill_id, status, created_at);
