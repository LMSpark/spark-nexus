package com.dsyg.platform.modules;

import com.dsyg.platform.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class GatewayController {
    private final String corsAllowedOrigins;

    public GatewayController(@Value("${platform.cors.allowed-origins}") String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/auth/login",
        "/api/health",
        "/api/registrations/neighborhood-options",
        "/api/registrations/tenants",
        "/api/registrations/communities",
        "/api/resident/register",
        "/api/payments/*/callback",
        "/api/bank/callbacks/*"
    );

    @GetMapping("/gateway/status")
    public GatewayStatus gatewayStatus(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return new GatewayStatus(
            "UP",
            "/api",
            "Spring MVC Gateway Facade",
            parseCsv(corsAllowedOrigins),
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
            PUBLIC_PATHS,
            routeGroups(),
            List.of(
                new GatewayPolicy("JWT_AUTH", "PROTECTED_API", "除公开路径外均要求 Bearer Token，并把 principal 注入请求上下文"),
                new GatewayPolicy("RBAC", "ROLE_AND_SCOPE", "控制器按角色与数据授权二次校验小区/租户访问范围"),
                new GatewayPolicy("CALLBACK_SIGNATURE", "PUBLIC_CALLBACK", "支付/银行回调免登录但要求签名、时间戳、nonce 与幂等台账"),
                new GatewayPolicy("CORS", "CONFIGURED_ORIGINS", "按 platform.cors.allowed-origins 配置限制 /api/** 跨域来源")
            ),
            LocalDateTime.now()
        );
    }

    private List<RouteGroup> routeGroups() {
        return List.of(
            new RouteGroup("AUTH", "/api/auth/**", "认证、租户切换、能力菜单", List.of("PUBLIC_LOGIN", "USER")),
            new RouteGroup("REGISTRATION", "/api/registrations/**,/api/tenants/**,/api/authorizations/**", "多租户注册准入与数据授权", List.of("ADMIN", "GOVERNMENT", "STREET")),
            new RouteGroup("COMMUNITY", "/api/communities,/api/houses,/api/owners/**", "小区、房屋、业主门户", List.of("ADMIN", "GOVERNMENT", "STREET", "NEIGHBORHOOD", "PROPERTY", "COMMITTEE", "OWNER")),
            new RouteGroup("NEIGHBORHOOD", "/api/neighborhood/**", "居委会事项、网格治理、关爱走访、资源联动", List.of("ADMIN", "GOVERNMENT", "STREET", "NEIGHBORHOOD")),
            new RouteGroup("BILLING_PAYMENT", "/api/billing/**,/api/payments/**", "收费、支付、退款、渠道账单", List.of("ADMIN", "PROPERTY", "OWNER", "CALLBACK")),
            new RouteGroup("REVENUE_EXPENSE", "/api/revenue,/api/expenses/**,/api/approvals/**", "公共收益、支出、审批流", List.of("ADMIN", "COMMITTEE", "STREET", "GOVERNMENT")),
            new RouteGroup("GOVERNANCE", "/api/votes/**,/api/work-orders/**,/api/announcements,/api/messages/**", "投票问卷、报修投诉、公告消息、微信治理通知", List.of("ADMIN", "GOVERNMENT", "STREET", "NEIGHBORHOOD", "COMMITTEE", "PROPERTY", "BANK", "MERCHANT", "OWNER")),
            new RouteGroup("FINANCE", "/api/finance/**", "凭证、账簿、现金流量表、PDF 导出", List.of("ADMIN", "COMMITTEE", "GOVERNMENT", "STREET")),
            new RouteGroup("BANK", "/api/bank/**", "银行配置、流水、对账、放款回执", List.of("ADMIN", "BANK", "GOVERNMENT", "STREET", "CALLBACK")),
            new RouteGroup("SUPERVISION", "/api/supervision/**", "监管驾驶舱、大屏、预警、信用评分", List.of("ADMIN", "GOVERNMENT", "STREET")),
            new RouteGroup("AUDIT_INTEGRATION", "/api/audit/**,/api/integrations/**,/api/acceptance/**,/api/gateway/**", "审计、外部适配器、验收中心、网关状态", List.of("ADMIN", "GOVERNMENT", "STREET"))
        );
    }

    private void requireAnyRole(HttpServletRequest request, String... roles) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录或登录已失效");
        }
        for (String role : roles) {
            if (role.equals(principal.role())) {
                return;
            }
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "当前角色无权查看网关状态");
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of("http://localhost:*", "http://127.0.0.1:*");
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    public record GatewayStatus(String status, String apiPrefix, String gatewayMode, List<String> allowedOrigins,
                                List<String> allowedMethods, List<String> publicPaths, List<RouteGroup> routeGroups,
                                List<GatewayPolicy> policies, LocalDateTime checkedAt) {}
    public record RouteGroup(String moduleCode, String pathPattern, String description, List<String> roles) {}
    public record GatewayPolicy(String policyCode, String policyType, String description) {}
}
