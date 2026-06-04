insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 6, 1, 'EXPENSE', 'WRITE', 'ACTIVE', now(), null, now()
where not exists (
  select 1 from data_authorization
  where grantee_tenant_id = 6
    and community_id = 1
    and data_scope = 'EXPENSE'
    and permission_code = 'WRITE'
    and status = 'ACTIVE'
);
