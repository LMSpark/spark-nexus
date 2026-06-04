package com.dsyg.platform.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AccessControlService {
    private final JdbcClient jdbc;

    public AccessControlService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> allowedCommunityIds(TokenService.Principal principal, String dataScope, String action) {
        if (isPlatformAdmin(principal)) {
            return jdbc.sql("select id from community order by id").query(Long.class).list();
        }
        if (principal.tenantId() == 0 || "OWNER".equals(principal.role())) {
            return List.of(principal.communityId());
        }
        return jdbc.sql("""
                select distinct r.community_id
                from tenant_community_relation r
                left join data_authorization a on a.grantee_tenant_id = r.tenant_id
                  and a.community_id = r.community_id
                  and a.data_scope = :dataScope
                  and a.permission_code = :action
                  and a.status = 'ACTIVE'
                  and (a.effective_to is null or a.effective_to > now())
                where r.tenant_id = :tenantId
                  and r.status = 'ACTIVE'
                  and (:dataScope is null or a.id is not null)
                order by r.community_id
                """)
            .param("tenantId", principal.tenantId())
            .param("dataScope", dataScope)
            .param("action", action)
            .query(Long.class)
            .list();
    }

    public boolean hasCommunityAccess(TokenService.Principal principal, long communityId, String dataScope, String action) {
        return allowedCommunityIds(principal, dataScope, action).contains(communityId);
    }

    public void assertCommunityAccess(TokenService.Principal principal, long communityId, String dataScope, String action) {
        if (!hasCommunityAccess(principal, communityId, dataScope, action)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问当前小区或数据范围");
        }
    }

    public void assertTenantAdmin(TokenService.Principal principal) {
        if (!isPlatformAdmin(principal) && !"GOVERNMENT".equals(principal.role()) && !"STREET".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权审核注册申请");
        }
    }

    public boolean isPlatformAdmin(TokenService.Principal principal) {
        return "ADMIN".equals(principal.role());
    }
}
