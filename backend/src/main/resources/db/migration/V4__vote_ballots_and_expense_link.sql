alter table vote add column related_expense_id bigint null;
alter table vote add column total_voters int not null default 0;
alter table vote add column agree_count int not null default 0;
alter table vote add column disagree_count int not null default 0;

create table vote_ballot (
  id bigint primary key auto_increment,
  vote_id bigint not null,
  owner_id bigint not null,
  decision varchar(16) not null,
  signature_hash varchar(128) not null,
  cast_at datetime not null,
  unique key uk_vote_owner(vote_id, owner_id)
);

update vote
set total_voters = (select households from community where community.id = vote.community_id),
    agree_count = round((select households from community where community.id = vote.community_id) * participation_rate / 100 * agree_rate / 100),
    disagree_count = greatest(round((select households from community where community.id = vote.community_id) * participation_rate / 100) - round((select households from community where community.id = vote.community_id) * participation_rate / 100 * agree_rate / 100), 0);
