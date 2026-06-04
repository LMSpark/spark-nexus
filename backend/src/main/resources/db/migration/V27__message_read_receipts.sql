create table message_read_receipt (
    id bigint primary key auto_increment,
    message_id bigint not null,
    user_id bigint not null,
    read_at datetime not null,
    unique(message_id, user_id)
);

create index idx_message_read_user on message_read_receipt(user_id, read_at);
