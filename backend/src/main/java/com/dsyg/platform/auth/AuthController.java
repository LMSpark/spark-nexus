package com.dsyg.platform.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcClient jdbc;
    private final TokenService tokenService;
    private final AccessControlService access;

    public AuthController(JdbcClient jdbc, TokenService tokenService, AccessControlService access) {
        this.jdbc = jdbc;
        this.tokenService = tokenService;
        this.access = access;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        UserRow user = jdbc.sql("""
                select id, username, display_name, role, community_id
                from app_user where username = :username and password_hash = :password
                """)
            .param("username", request.username())
            .param("password", request.password())
            .query(UserRow.class)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
        List<TenantOption> tenants = jdbc.sql("""
                select t.id tenantId, t.tenant_name tenantName, t.tenant_type tenantType, r.role_code roleCode, r.is_default isDefault
                from user_tenant_relation r
                join tenant t on t.id = r.tenant_id
                where r.user_id = :userId and r.status = 'ACTIVE' and t.status = 'ACTIVE'
                order by r.is_default desc, t.id
                """)
            .param("userId", user.id())
            .query(TenantOption.class)
            .list();
        TenantOption activeTenant = tenants.stream()
            .filter(item -> request.tenantId() != null && item.tenantId() == request.tenantId())
            .findFirst()
            .orElseGet(() -> tenants.stream().findFirst().orElse(new TenantOption(0, "兼容模式", "LEGACY", user.role(), 1)));
        String role = activeTenant.tenantId() == 0 ? user.role() : activeTenant.roleCode();
        return new LoginResponse(
            tokenService.issue(user.username(), role, user.communityId(), activeTenant.tenantId()),
            user.username(),
            user.displayName(),
            role,
            user.communityId(),
            activeTenant.tenantId(),
            tenants,
            Map.of("expiresIn", 86400)
        );
    }

    @PostMapping("/switch-tenant")
    public LoginResponse switchTenant(@RequestBody SwitchTenantRequest request, HttpServletRequest httpRequest) {
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        UserRow user = jdbc.sql("""
                select id, username, display_name, role, community_id
                from app_user where username = :username
                """)
            .param("username", principal.username())
            .query(UserRow.class)
            .single();
        List<TenantOption> tenants = tenantOptions(user.id());
        TenantOption activeTenant = tenants.stream()
            .filter(item -> item.tenantId() == request.tenantId())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("当前账号无权切换到该机构"));
        return new LoginResponse(
            tokenService.issue(user.username(), activeTenant.roleCode(), user.communityId(), activeTenant.tenantId()),
            user.username(),
            user.displayName(),
            activeTenant.roleCode(),
            user.communityId(),
            activeTenant.tenantId(),
            tenants,
            Map.of("expiresIn", 86400)
        );
    }

    @GetMapping("/capabilities")
    public CapabilityResponse capabilities(HttpServletRequest request) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<CapabilityRow> capabilities;
        if (access.isPlatformAdmin(principal)) {
            capabilities = jdbc.sql("""
                    select scope_name dataScope, permission_code permissionCode, community_count communityCount
                    from (
                      select 'COMMUNITY_PROFILE' scope_name union all select 'HOUSE' union all select 'BILLING' union all
                      select 'PAYMENT' union all select 'BANK_FLOW' union all select 'REVENUE' union all
                      select 'EXPENSE' union all select 'VOTE' union all select 'REPAIR' union all
                      select 'COMPLAINT' union all select 'FINANCE' union all select 'AUDIT'
                    ) scopes
                    cross join (
                      select 'READ' permission_code union all select 'WRITE' union all select 'APPROVE' union all select 'EXPORT'
                    ) permissions
                    cross join (select count(*) community_count from community) communities
                    order by scope_name, permission_code
                    """)
                .query(CapabilityRow.class)
                .list();
        } else {
            capabilities = jdbc.sql("""
                    select a.data_scope dataScope, a.permission_code permissionCode, count(distinct a.community_id) communityCount
                    from data_authorization a
                    join tenant_community_relation r on r.tenant_id = a.grantee_tenant_id
                      and r.community_id = a.community_id and r.status = 'ACTIVE'
                    where a.grantee_tenant_id = :tenantId
                      and a.status = 'ACTIVE'
                      and (a.effective_to is null or a.effective_to > now())
                    group by a.data_scope, a.permission_code
                    order by a.data_scope, a.permission_code
                    """)
                .param("tenantId", principal.tenantId())
                .query(CapabilityRow.class)
                .list();
        }
        List<String> navKeys = navKeys(principal.role(), capabilities);
        return new CapabilityResponse(principal.role(), principal.tenantId(), capabilities, navKeys);
    }

    private List<String> navKeys(String role, List<CapabilityRow> capabilities) {
        if ("ADMIN".equals(role)) {
            return List.of("dashboard", "screen", "communities", "billing", "revenue", "expenses", "votes", "repairs", "finance", "banking", "registrations", "acceptance", "audit");
        }
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        addIf(keys, capabilities, "COMMUNITY_PROFILE", "READ", "communities");
        addIf(keys, capabilities, "BILLING", "READ", "billing");
        addIf(keys, capabilities, "REVENUE", "READ", "revenue");
        addIf(keys, capabilities, "EXPENSE", "READ", "expenses");
        addIf(keys, capabilities, "VOTE", "READ", "votes");
        addIf(keys, capabilities, "REPAIR", "READ", "repairs");
        addIf(keys, capabilities, "COMPLAINT", "READ", "repairs");
        addIf(keys, capabilities, "FINANCE", "READ", "finance");
        addIf(keys, capabilities, "BANK_FLOW", "READ", "banking");
        if ("GOVERNMENT".equals(role) || "STREET".equals(role)) {
            keys.add("dashboard");
            keys.add("screen");
            keys.add("registrations");
            keys.add("acceptance");
            keys.add("audit");
        }
        if (!keys.isEmpty() && ("PROPERTY".equals(role) || "COMMITTEE".equals(role) || "BANK".equals(role))) {
            keys.add("acceptance");
        }
        return List.copyOf(keys);
    }

    private void addIf(java.util.LinkedHashSet<String> keys, List<CapabilityRow> capabilities, String dataScope, String permissionCode, String navKey) {
        boolean matched = capabilities.stream().anyMatch(item ->
            dataScope.equals(item.dataScope()) && permissionCode.equals(item.permissionCode()) && item.communityCount() > 0
        );
        if (matched) {
            keys.add(navKey);
        }
    }

    private List<TenantOption> tenantOptions(long userId) {
        return jdbc.sql("""
                select t.id tenantId, t.tenant_name tenantName, t.tenant_type tenantType, r.role_code roleCode, r.is_default isDefault
                from user_tenant_relation r
                join tenant t on t.id = r.tenant_id
                where r.user_id = :userId and r.status = 'ACTIVE' and t.status = 'ACTIVE'
                order by r.is_default desc, t.id
                """)
            .param("userId", userId)
            .query(TenantOption.class)
            .list();
    }

    public record LoginRequest(String username, String password, Long tenantId) {}
    public record SwitchTenantRequest(long tenantId) {}
    public record LoginResponse(String token, String username, String displayName, String role, long communityId, long tenantId, List<TenantOption> tenants, Map<String, Object> meta) {}
    public record CapabilityResponse(String role, long tenantId, List<CapabilityRow> capabilities, List<String> navKeys) {}
    public record CapabilityRow(String dataScope, String permissionCode, int communityCount) {}
    public record UserRow(long id, String username, String displayName, String role, long communityId) {}
    public record TenantOption(long tenantId, String tenantName, String tenantType, String roleCode, int isDefault) {}
}
