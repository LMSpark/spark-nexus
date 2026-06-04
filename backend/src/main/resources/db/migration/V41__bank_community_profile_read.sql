insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id,
                               data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, r.tenant_id, r.community_id,
       'COMMUNITY_PROFILE', 'READ', 'ACTIVE', now(), null, now()
from tenant_community_relation r
join tenant t on t.id = r.tenant_id
where t.tenant_type = 'BANK'
  and r.status = 'ACTIVE'
  and r.relation_type like 'BANK_%'
  and not exists (
    select 1 from data_authorization a
    where a.grantee_tenant_id = r.tenant_id
      and a.community_id = r.community_id
      and a.data_scope = 'COMMUNITY_PROFILE'
      and a.permission_code = 'READ'
      and a.status = 'ACTIVE'
  );
