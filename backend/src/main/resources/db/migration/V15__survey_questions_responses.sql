create table survey_question (
  id bigint primary key auto_increment,
  vote_id bigint not null,
  question_title varchar(255) not null,
  question_type varchar(32) not null,
  sort_no int not null
);

create table survey_option (
  id bigint primary key auto_increment,
  question_id bigint not null,
  option_label varchar(128) not null,
  sort_no int not null
);

create table survey_response (
  id bigint primary key auto_increment,
  vote_id bigint not null,
  question_id bigint not null,
  option_id bigint not null,
  owner_id bigint not null,
  submitted_at datetime not null,
  unique key uk_survey_response_owner_question(vote_id, question_id, owner_id)
);

create index idx_survey_question_vote on survey_question(vote_id, sort_no);
create index idx_survey_response_vote on survey_response(vote_id, question_id, option_id);

insert into survey_question(vote_id, question_title, question_type, sort_no)
select id, '您对本小区公共收益管理透明度是否满意？', 'SINGLE', 1
from vote
where vote_type = 'SURVEY';

insert into survey_option(question_id, option_label, sort_no)
select q.id, '满意', 1 from survey_question q
union all
select q.id, '基本满意', 2 from survey_question q
union all
select q.id, '不满意', 3 from survey_question q;
