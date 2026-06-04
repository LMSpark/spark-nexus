create table community_credit_factor (
    id bigint primary key auto_increment,
    score_id bigint not null,
    community_id bigint not null,
    factor_code varchar(60) not null,
    factor_name varchar(100) not null,
    factor_value varchar(120) not null,
    weight decimal(8,2) not null,
    deduction decimal(8,2) not null,
    evidence varchar(500) not null,
    created_at datetime not null
);

create index idx_community_credit_factor_score on community_credit_factor(score_id);
create index idx_community_credit_factor_community on community_credit_factor(community_id, created_at);
