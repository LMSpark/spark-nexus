package com.dsyg.platform.modules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dsyg.platform.auth.AccessControlService;
import com.dsyg.platform.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private final String mode;
    private final String smsProvider;
    private final String identityProvider;
    private final String jwtSecret;
    private final String encryptionKey;
    private final String wechatMchId;
    private final String wechatAppId;
    private final String wechatCallbackSecret;
    private final String alipayAppId;
    private final String alipayMerchantId;
    private final String alipayPrivateKey;
    private final String alipayPublicKey;
    private final String alipayCallbackSecret;
    private final String bankBaseUrl;
    private final String bankMerchantId;
    private final String bankCallbackSecret;
    private final String smsApiKey;
    private final String identityApiKey;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String redisHost;
    private final int redisPort;
    private final String minioEndpoint;
    private final String minioConsoleEndpoint;
    private final String corsAllowedOrigins;
    private final JdbcClient jdbc;
    private final IntegrationService integrations;
    private final AccessControlService accessControl;

    public IntegrationController(
        @Value("${platform.integrations.mode}") String mode,
        @Value("${platform.integrations.sms.provider}") String smsProvider,
        @Value("${platform.integrations.identity.provider}") String identityProvider,
        @Value("${platform.jwt-secret}") String jwtSecret,
        @Value("${platform.security.encryption-key:dev-sensitive-key-change-me}") String encryptionKey,
        @Value("${platform.integrations.wechat.mch-id:}") String wechatMchId,
        @Value("${platform.integrations.wechat.app-id:}") String wechatAppId,
        @Value("${platform.integrations.wechat.callback-secret:}") String wechatCallbackSecret,
        @Value("${platform.integrations.alipay.app-id:}") String alipayAppId,
        @Value("${platform.integrations.alipay.merchant-id:}") String alipayMerchantId,
        @Value("${platform.integrations.alipay.private-key:}") String alipayPrivateKey,
        @Value("${platform.integrations.alipay.public-key:}") String alipayPublicKey,
        @Value("${platform.integrations.alipay.callback-secret:}") String alipayCallbackSecret,
        @Value("${platform.integrations.bank.base-url:}") String bankBaseUrl,
        @Value("${platform.integrations.bank.merchant-id:}") String bankMerchantId,
        @Value("${platform.integrations.bank.callback-secret:}") String bankCallbackSecret,
        @Value("${platform.integrations.sms.api-key:}") String smsApiKey,
        @Value("${platform.integrations.identity.api-key:}") String identityApiKey,
        @Value("${spring.datasource.url}") String datasourceUrl,
        @Value("${spring.datasource.username:}") String datasourceUsername,
        @Value("${REDIS_HOST:localhost}") String redisHost,
        @Value("${REDIS_PORT:6379}") int redisPort,
        @Value("${MINIO_ENDPOINT:http://localhost:9000}") String minioEndpoint,
        @Value("${MINIO_CONSOLE_ENDPOINT:http://localhost:9001}") String minioConsoleEndpoint,
        @Value("${platform.cors.allowed-origins}") String corsAllowedOrigins,
        JdbcClient jdbc,
        IntegrationService integrations,
        AccessControlService accessControl
    ) {
        this.mode = mode;
        this.smsProvider = smsProvider;
        this.identityProvider = identityProvider;
        this.jwtSecret = jwtSecret;
        this.encryptionKey = encryptionKey;
        this.wechatMchId = wechatMchId;
        this.wechatAppId = wechatAppId;
        this.wechatCallbackSecret = wechatCallbackSecret;
        this.alipayAppId = alipayAppId;
        this.alipayMerchantId = alipayMerchantId;
        this.alipayPrivateKey = alipayPrivateKey;
        this.alipayPublicKey = alipayPublicKey;
        this.alipayCallbackSecret = alipayCallbackSecret;
        this.bankBaseUrl = bankBaseUrl;
        this.bankMerchantId = bankMerchantId;
        this.bankCallbackSecret = bankCallbackSecret;
        this.smsApiKey = smsApiKey;
        this.identityApiKey = identityApiKey;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.minioEndpoint = minioEndpoint;
        this.minioConsoleEndpoint = minioConsoleEndpoint;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.jdbc = jdbc;
        this.integrations = integrations;
        this.accessControl = accessControl;
    }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return Map.of(
            "mode", mode,
            "adapters", List.of(
                Map.of("code", "WECHAT_PAY", "name", "微信支付", "status", adapterStatus()),
                Map.of("code", "WECHAT_MESSAGE", "name", "微信社区通知", "status", adapterStatus()),
                Map.of("code", "ALIPAY", "name", "支付宝支付", "status", adapterStatus()),
                Map.of("code", "BANK_DIRECT", "name", "银行直连", "status", adapterStatus()),
                Map.of("code", "SMS", "name", "短信通知", "status", "dev".equals(smsProvider) ? "DEV_SIMULATED" : "REAL_CONFIGURED"),
                Map.of("code", "IDENTITY", "name", "实名认证", "status", "dev".equals(identityProvider) ? "DEV_SIMULATED" : "REAL_CONFIGURED")
            )
        );
    }

    @GetMapping("/configs")
    public List<IntegrationConfig> configs(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, adapter_code adapterCode, provider_name providerName, mode, endpoint_url endpointUrl,
                   merchant_no merchantNo, status, updated_at updatedAt
            from integration_config
            order by id
            """).query(IntegrationConfig.class).list();
    }

    @PostMapping("/configs/sync")
    public Map<String, Object> syncConfigsFromEnvironment(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN");
        List<AdapterConfigDraft> configs = List.of(
            new AdapterConfigDraft("WECHAT_PAY", "微信支付商户平台", mode, "https://api.mch.weixin.qq.com", wechatMchId, adapterConfigStatus(wechatMchId, wechatAppId, wechatCallbackSecret)),
            new AdapterConfigDraft("WECHAT_MESSAGE", "微信小程序/公众号社区通知", mode, "https://api.weixin.qq.com", wechatAppId, adapterConfigStatus(wechatAppId)),
            new AdapterConfigDraft("ALIPAY", "支付宝支付", mode, "https://openapi.alipay.com/gateway.do", alipayMerchantId, adapterConfigStatus(alipayAppId, alipayMerchantId, alipayPrivateKey, alipayPublicKey, alipayCallbackSecret)),
            new AdapterConfigDraft("BANK_DIRECT", "银行直连监管接口", mode, bankBaseUrl, bankMerchantId, adapterConfigStatus(bankBaseUrl, bankMerchantId, bankCallbackSecret)),
            new AdapterConfigDraft("SMS", "短信通知网关", smsProvider, "", "", adapterConfigStatusForProvider(smsProvider, smsApiKey)),
            new AdapterConfigDraft("IDENTITY", "实名认证核验服务", identityProvider, "", "", adapterConfigStatusForProvider(identityProvider, identityApiKey))
        );
        int synced = 0;
        for (AdapterConfigDraft config : configs) {
            Integer exists = jdbc.sql("select count(*) from integration_config where adapter_code = :adapterCode")
                .param("adapterCode", config.adapterCode())
                .query(Integer.class)
                .single();
            if (exists != null && exists > 0) {
                jdbc.sql("""
                    update integration_config
                    set provider_name = :providerName, mode = :mode, endpoint_url = :endpointUrl,
                        merchant_no = :merchantNo, status = :status, updated_at = now()
                    where adapter_code = :adapterCode
                    """)
                    .param("providerName", config.providerName())
                    .param("mode", config.mode())
                    .param("endpointUrl", blankToNull(config.endpointUrl()))
                    .param("merchantNo", blankToNull(config.merchantNo()))
                    .param("status", config.status())
                    .param("adapterCode", config.adapterCode())
                    .update();
            } else {
                jdbc.sql("""
                    insert into integration_config(adapter_code, provider_name, mode, endpoint_url, merchant_no, status, updated_at)
                    values(:adapterCode, :providerName, :mode, :endpointUrl, :merchantNo, :status, now())
                    """)
                    .param("adapterCode", config.adapterCode())
                    .param("providerName", config.providerName())
                    .param("mode", config.mode())
                    .param("endpointUrl", blankToNull(config.endpointUrl()))
                    .param("merchantNo", blankToNull(config.merchantNo()))
                    .param("status", config.status())
                    .update();
            }
            synced++;
        }
        jdbc.sql("""
            insert into integration_call_log(adapter_code, operation, request_summary, response_summary, status, trace_no, created_at)
            values('SECURITY', 'SYNC_INTEGRATION_CONFIG', :requestSummary, :responseSummary, 'SUCCESS', :traceNo, now())
            """)
            .param("requestSummary", "mode=" + mode + ", providers=" + configs.size())
            .param("responseSummary", "synced=" + synced + ", inactive=" + configs.stream().filter(item -> !"ACTIVE".equals(item.status())).count())
            .param("traceNo", "CFG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .update();
        return Map.of("syncedCount", synced, "configs", configs);
    }

    @GetMapping("/calls")
    public List<IntegrationCallLog> calls(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, adapter_code adapterCode, operation, request_summary requestSummary,
                   response_summary responseSummary, status, trace_no traceNo, created_at createdAt
            from integration_call_log
            order by created_at desc limit 80
            """).query(IntegrationCallLog.class).list();
    }

    @GetMapping("/callbacks")
    public List<ExternalCallbackReceipt> callbacks(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, adapter_code adapterCode, event_type eventType, business_no businessNo,
                   signature_status signatureStatus, http_status httpStatus, request_digest requestDigest,
                   response_summary responseSummary, trace_no traceNo, idempotency_key idempotencyKey,
                   process_status processStatus, received_at receivedAt
            from external_callback_receipt
            order by received_at desc, id desc
            limit 120
            """).query(ExternalCallbackReceipt.class).list();
    }

    @GetMapping("/callbacks/export")
    public ResponseEntity<byte[]> exportCallbacks(HttpServletRequest request) {
        List<ExternalCallbackReceipt> rows = callbacks(request);
        StringBuilder csv = new StringBuilder("适配器,事件类型,业务编号,验签状态,处理状态,HTTP状态,幂等键,请求摘要,响应摘要,追踪号,接收时间\n");
        for (ExternalCallbackReceipt row : rows) {
            csv.append(csv(row.adapterCode())).append(',')
                .append(csv(row.eventType())).append(',')
                .append(csv(row.businessNo())).append(',')
                .append(csv(row.signatureStatus())).append(',')
                .append(csv(row.processStatus())).append(',')
                .append(row.httpStatus()).append(',')
                .append(csv(row.idempotencyKey())).append(',')
                .append(csv(row.requestDigest())).append(',')
                .append(csv(row.responseSummary())).append(',')
                .append(csv(row.traceNo())).append(',')
                .append(csv(row.receivedAt() == null ? "" : row.receivedAt().toString()))
                .append('\n');
        }
        logDataExport(request, "EXTERNAL_CALLBACK", "external-callback-receipts.csv", "external_callback_receipt", 0, rows.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"external-callback-receipts.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/callback-nonces")
    public List<ExternalCallbackNonce> callbackNonces(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, adapter_code adapterCode, event_type eventType, business_no businessNo,
                   nonce_value nonceValue, callback_timestamp callbackTimestamp, received_at receivedAt
            from external_callback_nonce
            order by received_at desc, id desc
            limit 120
            """).query(ExternalCallbackNonce.class).list();
    }

    @GetMapping("/callback-nonces/export")
    public ResponseEntity<byte[]> exportCallbackNonces(HttpServletRequest request) {
        List<ExternalCallbackNonce> rows = callbackNonces(request);
        StringBuilder csv = new StringBuilder("适配器,事件类型,业务编号,nonce,回调时间戳,接收时间\n");
        for (ExternalCallbackNonce row : rows) {
            csv.append(csv(row.adapterCode())).append(',')
                .append(csv(row.eventType())).append(',')
                .append(csv(row.businessNo())).append(',')
                .append(csv(row.nonceValue())).append(',')
                .append(row.callbackTimestamp()).append(',')
                .append(csv(row.receivedAt() == null ? "" : row.receivedAt().toString()))
                .append('\n');
        }
        logDataExport(request, "EXTERNAL_CALLBACK_NONCE", "external-callback-nonces.csv", "external_callback_nonce", 0, rows.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"external-callback-nonces.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/database-compatibility")
    public DatabaseCompatibility databaseCompatibility(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        String currentVendor = detectDatabaseVendor(datasourceUrl);
        List<DatabaseCompatibilityItem> items = buildDatabaseCompatibilityItems(currentVendor);
        long passCount = items.stream().filter(item -> "PASS".equals(item.checkResult())).count();
        long activeCount = items.stream().filter(item -> "CURRENT".equals(item.status())).count();
        String status = activeCount > 0 && passCount == items.size() ? "PASS" : "READY_WITH_ACTIONS";
        return new DatabaseCompatibility(
            status,
            currentVendor,
            maskJdbcUrl(datasourceUrl),
            mask(datasourceUsername),
            items,
            "本地默认 MySQL 8；国产库切换通过 DB_URL/DB_USERNAME/DB_PASSWORD 和对应 JDBC Driver 完成，迁移前先导出本矩阵作为整改清单。"
        );
    }

    @GetMapping("/database-compatibility/export")
    public ResponseEntity<byte[]> exportDatabaseCompatibility(HttpServletRequest request) {
        DatabaseCompatibility compatibility = databaseCompatibility(request);
        StringBuilder csv = new StringBuilder("数据库,状态,检查结果,JDBC前缀,驱动类,Flyway方言,分页/时间函数,迁移动作\n");
        for (DatabaseCompatibilityItem item : compatibility.items()) {
            csv.append(csv(item.vendorName())).append(',')
                .append(csv(item.status())).append(',')
                .append(csv(item.checkResult())).append(',')
                .append(csv(item.jdbcPrefix())).append(',')
                .append(csv(item.driverClass())).append(',')
                .append(csv(item.flywayDialect())).append(',')
                .append(csv(item.sqlCompatibility())).append(',')
                .append(csv(item.migrationAction()))
                .append('\n');
        }
        logDataExport(request, "DATABASE_COMPATIBILITY", "database-compatibility.csv", "database_compatibility", 0, compatibility.items().size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"database-compatibility.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/deployment-readiness")
    public DeploymentReadiness deploymentReadiness(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        List<DeploymentReadinessItem> items = buildDeploymentReadinessItems();
        long blocking = items.stream().filter(item -> item.required() && !"PASS".equals(item.status())).count();
        long warnings = items.stream().filter(item -> !item.required() && !"PASS".equals(item.status())).count();
        String status = blocking > 0 ? "BLOCKED" : warnings > 0 ? "READY_WITH_WARNINGS" : "READY";
        return new DeploymentReadiness(
            status,
            LocalDateTime.now(),
            items,
            "生产部署需先确保 MySQL/Flyway 必需项通过；Redis 与 MinIO 当前按可选增强项检查，可按 deploy/docker-compose.yml 启用。"
        );
    }

    @GetMapping("/deployment-readiness/export")
    public ResponseEntity<byte[]> exportDeploymentReadiness(HttpServletRequest request) {
        DeploymentReadiness readiness = deploymentReadiness(request);
        StringBuilder csv = new StringBuilder("组件,状态,是否必需,端点,证据,整改动作\n");
        for (DeploymentReadinessItem item : readiness.items()) {
            csv.append(csv(item.component())).append(',')
                .append(csv(item.status())).append(',')
                .append(csv(item.required() ? "YES" : "NO")).append(',')
                .append(csv(item.endpoint())).append(',')
                .append(csv(item.evidence())).append(',')
                .append(csv(item.action()))
                .append('\n');
        }
        logDataExport(request, "DEPLOYMENT_READINESS", "deployment-readiness.csv", "deployment_readiness", 0, readiness.items().size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"deployment-readiness.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/data-exchange/packages")
    public List<DataExchangePackage> dataExchangePackages(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (communityId != null) {
            accessControl.assertCommunityAccess(principal, communityId, "COMMUNITY_PROFILE", "READ");
        }
        return jdbc.sql("""
            select p.id, p.package_no packageNo, p.target_party targetParty, c.name communityName,
                   p.data_domains dataDomains, p.record_count recordCount, p.checksum,
                   p.status, p.created_by createdBy, p.created_at createdAt
            from data_exchange_package p
            left join community c on c.id = p.community_id
            where (:communityId is null or p.community_id = :communityId)
              and (p.community_id is null or p.community_id in (:allowedCommunityIds))
            order by p.created_at desc, p.id desc
            limit 120
            """)
            .param("communityId", communityId)
            .param("allowedCommunityIds", allowedCommunityIds.isEmpty() ? List.of(-1L) : allowedCommunityIds)
            .query(DataExchangePackage.class)
            .list();
    }

    @PostMapping("/data-exchange/packages")
    public Map<String, Object> createDataExchangePackage(@RequestBody DataExchangePackageRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<String> domains = body.dataDomains() == null || body.dataDomains().isEmpty()
            ? List.of("BILLING", "BANK_FLOW", "PUBLIC_REVENUE", "RISK_ALERT", "CREDIT_SCORE", "EXPORT_LOG")
            : body.dataDomains().stream().filter(item -> item != null && !item.isBlank()).map(String::trim).distinct().toList();
        Long communityId = body.communityId();
        if (communityId != null) {
            accessControl.assertCommunityAccess(principal, communityId, "COMMUNITY_PROFILE", "READ");
        }
        int recordCount = domains.stream().map(domain -> dataExchangeDomainCount(domain, communityId)).reduce(0, Integer::sum);
        String targetParty = body.targetParty() == null || body.targetParty().isBlank() ? "GOVERNMENT" : body.targetParty().trim().toUpperCase();
        String packageNo = "DX-" + targetParty + "-" + System.currentTimeMillis();
        String dataDomains = String.join(",", domains);
        String checksum = Integer.toHexString((packageNo + "|" + targetParty + "|" + communityId + "|" + dataDomains + "|" + recordCount).hashCode());
        jdbc.sql("""
            insert into data_exchange_package(package_no, target_party, community_id, data_domains,
                                              record_count, checksum, status, created_by, created_at)
            values(:packageNo, :targetParty, :communityId, :dataDomains,
                   :recordCount, :checksum, 'READY', :createdBy, now())
            """)
            .param("packageNo", packageNo)
            .param("targetParty", targetParty)
            .param("communityId", communityId)
            .param("dataDomains", dataDomains)
            .param("recordCount", recordCount)
            .param("checksum", checksum)
            .param("createdBy", principal.username())
            .update();
        jdbc.sql("""
            insert into integration_call_log(adapter_code, operation, request_summary, response_summary, status, trace_no, created_at)
            values('DATA_EXCHANGE', 'BUILD_PACKAGE', :requestSummary, :responseSummary, 'SUCCESS', :traceNo, now())
            """)
            .param("requestSummary", "target=" + targetParty + ", community=" + (communityId == null ? "ALL" : communityId) + ", domains=" + dataDomains)
            .param("responseSummary", "packageNo=" + packageNo + ", records=" + recordCount + ", checksum=" + checksum)
            .param("traceNo", packageNo)
            .update();
        return Map.of("packageNo", packageNo, "targetParty", targetParty, "recordCount", recordCount, "checksum", checksum, "status", "READY");
    }

    @GetMapping("/data-exchange/packages/export")
    public ResponseEntity<byte[]> exportDataExchangePackages(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        List<DataExchangePackage> rows = dataExchangePackages(communityId, request);
        StringBuilder csv = new StringBuilder("交换包编号,目标方,小区,数据域,记录数,校验摘要,状态,创建人,创建时间\n");
        for (DataExchangePackage row : rows) {
            csv.append(csv(row.packageNo())).append(',')
                .append(csv(row.targetParty())).append(',')
                .append(csv(row.communityName())).append(',')
                .append(csv(row.dataDomains())).append(',')
                .append(row.recordCount()).append(',')
                .append(csv(row.checksum())).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.createdBy())).append(',')
                .append(csv(row.createdAt() == null ? "" : row.createdAt().toString()))
                .append('\n');
        }
        logDataExport(request, "DATA_EXCHANGE", "data-exchange-packages.csv", "data_exchange_package", 0, rows.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data-exchange-packages.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/security-checks")
    public List<IntegrationSecurityCheck> securityChecks(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, adapter_code adapterCode, check_item checkItem, check_result checkResult,
                   risk_level riskLevel, evidence, checked_at checkedAt
            from integration_security_check
            order by checked_at desc, id desc
            limit 100
            """).query(IntegrationSecurityCheck.class).list();
    }

    @GetMapping("/sensitive-fields")
    public List<SensitiveFieldAudit> sensitiveFieldAudits(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, target_table targetTable, target_id targetId, field_name fieldName,
                   protection, actor, access_purpose accessPurpose, created_at createdAt
            from sensitive_field_audit
            order by created_at desc, id desc
            limit 120
            """).query(SensitiveFieldAudit.class).list();
    }

    @GetMapping("/sensitive-fields/export")
    public ResponseEntity<byte[]> exportSensitiveFieldAudits(HttpServletRequest request) {
        List<SensitiveFieldAudit> rows = sensitiveFieldAudits(request);
        StringBuilder csv = new StringBuilder("对象表,对象ID,字段,保护方式,操作人,访问用途,时间\n");
        for (SensitiveFieldAudit row : rows) {
            csv.append(csv(row.targetTable())).append(',')
                .append(row.targetId()).append(',')
                .append(csv(row.fieldName())).append(',')
                .append(csv(row.protection())).append(',')
                .append(csv(row.actor())).append(',')
                .append(csv(row.accessPurpose())).append(',')
                .append(csv(row.createdAt() == null ? "" : row.createdAt().toString()))
                .append('\n');
        }
        logDataExport(request, "SENSITIVE_FIELD_AUDIT", "sensitive-field-audits.csv", "sensitive_field_audit", 0, rows.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sensitive-field-audits.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/production-readiness")
    public Map<String, Object> productionReadiness(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        List<SecurityCheckDraft> checks = buildSecurityChecks();
        long failed = checks.stream().filter(check -> "FAIL".equals(check.checkResult())).count();
        long highWarnings = checks.stream()
            .filter(check -> "WARN".equals(check.checkResult()) && "HIGH".equals(check.riskLevel()))
            .count();
        long mediumWarnings = checks.stream()
            .filter(check -> "WARN".equals(check.checkResult()) && "MEDIUM".equals(check.riskLevel()))
            .count();
        String readinessStatus = failed > 0 || highWarnings > 0 ? "BLOCKED" : mediumWarnings > 0 ? "WARN" : "READY";
        return Map.of(
            "mode", mode,
            "status", readinessStatus,
            "canSwitchProduction", failed == 0 && highWarnings == 0,
            "failed", failed,
            "highWarnings", highWarnings,
            "mediumWarnings", mediumWarnings,
            "checks", checks.stream()
                .map(check -> Map.of(
                    "adapterCode", check.adapterCode(),
                    "checkItem", check.checkItem(),
                    "checkResult", check.checkResult(),
                    "riskLevel", check.riskLevel(),
                    "evidence", check.evidence()
                ))
                .toList()
        );
    }

    @GetMapping("/production-readiness/export")
    public ResponseEntity<byte[]> exportProductionReadiness(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        List<SecurityCheckDraft> checks = buildSecurityChecks();
        long failed = checks.stream().filter(check -> "FAIL".equals(check.checkResult())).count();
        long highWarnings = checks.stream()
            .filter(check -> "WARN".equals(check.checkResult()) && "HIGH".equals(check.riskLevel()))
            .count();
        long mediumWarnings = checks.stream()
            .filter(check -> "WARN".equals(check.checkResult()) && "MEDIUM".equals(check.riskLevel()))
            .count();
        String readinessStatus = failed > 0 || highWarnings > 0 ? "BLOCKED" : mediumWarnings > 0 ? "WARN" : "READY";
        StringBuilder csv = new StringBuilder("门禁状态,当前模式,可切生产,失败项,高危预警,中危预警\n");
        csv.append(csv(readinessStatus)).append(',')
            .append(csv(mode)).append(',')
            .append(failed == 0 && highWarnings == 0 ? "是" : "否").append(',')
            .append(failed).append(',')
            .append(highWarnings).append(',')
            .append(mediumWarnings)
            .append("\n\n");
        csv.append("适配器,检查项,结果,风险等级,证据,整改动作\n");
        for (SecurityCheckDraft check : checks) {
            csv.append(csv(check.adapterCode())).append(',')
                .append(csv(check.checkItem())).append(',')
                .append(csv(check.checkResult())).append(',')
                .append(csv(check.riskLevel())).append(',')
                .append(csv(check.evidence())).append(',')
                .append(csv(remediationAction(check)))
                .append('\n');
        }
        logDataExport(request, "INTEGRATION_READINESS", "integration-production-readiness.csv", "integration_production_readiness", 0, checks.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"integration-production-readiness.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/security-checks/export")
    public ResponseEntity<byte[]> exportSecurityChecks(HttpServletRequest request) {
        List<IntegrationSecurityCheck> rows = securityChecks(request);
        StringBuilder csv = new StringBuilder("适配器,检查项,结果,风险等级,证据,检查时间\n");
        for (IntegrationSecurityCheck row : rows) {
            csv.append(csv(row.adapterCode())).append(',')
                .append(csv(row.checkItem())).append(',')
                .append(csv(row.checkResult())).append(',')
                .append(csv(row.riskLevel())).append(',')
                .append(csv(row.evidence())).append(',')
                .append(csv(row.checkedAt() == null ? "" : row.checkedAt().toString()))
                .append('\n');
        }
        logDataExport(request, "INTEGRATION_SECURITY", "integration-security-checks.csv", "integration_security_check", 0, rows.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"integration-security-checks.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/security-checks/run")
    public Map<String, Object> runSecurityChecks(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT");
        List<SecurityCheckDraft> checks = buildSecurityChecks();
        for (SecurityCheckDraft check : checks) {
            jdbc.sql("""
                insert into integration_security_check(adapter_code, check_item, check_result, risk_level, evidence, checked_at)
                values(:adapterCode, :checkItem, :checkResult, :riskLevel, :evidence, now())
                """)
                .param("adapterCode", check.adapterCode())
                .param("checkItem", check.checkItem())
                .param("checkResult", check.checkResult())
                .param("riskLevel", check.riskLevel())
                .param("evidence", check.evidence())
                .update();
        }
        long passed = checks.stream().filter(check -> "PASS".equals(check.checkResult())).count();
        long warnings = checks.stream().filter(check -> "WARN".equals(check.checkResult())).count();
        long failed = checks.stream().filter(check -> "FAIL".equals(check.checkResult())).count();
        jdbc.sql("""
            insert into integration_call_log(adapter_code, operation, request_summary, response_summary, status, trace_no, created_at)
            values('SECURITY', 'RUN_INTEGRATION_SECURITY_CHECK', :requestSummary, :responseSummary, :status, :traceNo, now())
            """)
            .param("requestSummary", "mode=" + mode + ", checks=" + checks.size())
            .param("responseSummary", "PASS=" + passed + ", WARN=" + warnings + ", FAIL=" + failed)
            .param("status", failed > 0 ? "WARNING" : "SUCCESS")
            .param("traceNo", "SEC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .update();
        return Map.of("total", checks.size(), "passed", passed, "warnings", warnings, "failed", failed);
    }

    @PostMapping("/sms/test")
    public Map<String, Object> testSms(@RequestBody SmsTestRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN");
        String phone = body.phone() == null || body.phone().isBlank() ? "13800000000" : body.phone();
        String content = body.content() == null || body.content().isBlank() ? "SPARK Nexus 平台短信通道测试" : body.content();
        return integrations.sendSms(phone, "TEST_NOTICE", content);
    }

    @PostMapping("/identity/test")
    public Map<String, Object> testIdentity(@RequestBody IdentityTestRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        String name = body.name() == null || body.name().isBlank() ? "张女士" : body.name();
        String phone = body.phone() == null || body.phone().isBlank() ? "13800001024" : body.phone();
        String identityNo = body.identityNo() == null || body.identityNo().isBlank() ? "420101199001012381" : body.identityNo();
        String expectedName = body.expectedName() == null || body.expectedName().isBlank() ? name : body.expectedName();
        String expectedPhoneMask = body.expectedPhoneMask() == null || body.expectedPhoneMask().isBlank() ? maskPhone(phone) : body.expectedPhoneMask();
        String expectedIdentityMask = body.expectedIdentityMask() == null || body.expectedIdentityMask().isBlank() ? maskIdentity(identityNo) : body.expectedIdentityMask();
        Map<String, Object> result = integrations.verifyIdentity(name, phone, identityNo, expectedName, expectedPhoneMask, expectedIdentityMask);
        logSensitiveFieldAudit("identity_test", 0, "phone", "MASKED_VERIFIED", principal.username(), "实名认证核验联调");
        logSensitiveFieldAudit("identity_test", 0, "identityNo", "MASKED_VERIFIED", principal.username(), "实名认证核验联调");
        return result;
    }

    @PostMapping("/callbacks/signature-diagnostics")
    public Map<String, Object> callbackSignatureDiagnostics(@RequestBody CallbackSignatureDiagnosticRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN");
        String provider = hasText(body.provider()) ? body.provider().trim().toUpperCase() : "WECHAT_PAY";
        String eventType = hasText(body.eventType()) ? body.eventType().trim().toUpperCase() : "PAYMENT_CALLBACK";
        String businessNo = body.businessNo() == null ? "" : body.businessNo().trim();
        String canonicalPayload = integrations.canonicalPayload(body.payload());
        Map<String, Object> verification = integrations.verifyExternalCallback(provider, eventType, businessNo, canonicalPayload, body.signature());
        String expectedSignature = "dev".equals(mode)
            ? integrations.expectedCallbackSignature(provider, businessNo, canonicalPayload)
            : "生产模式不回显期望签名，请使用渠道侧签名值比对验签结果";
        return Map.of(
            "provider", provider,
            "eventType", eventType,
            "businessNo", businessNo,
            "canonicalPayload", canonicalPayload,
            "signingBase", businessNo + "|" + canonicalPayload,
            "verified", verification.get("verified"),
            "signatureStatus", verification.get("signatureStatus"),
            "expectedSignature", expectedSignature,
            "mode", mode
        );
    }

    private String adapterStatus() {
        return "dev".equals(mode) ? "DEV_SIMULATED" : "REAL_CONFIGURED";
    }

    private void requireAnyRole(HttpServletRequest request, String... roles) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        for (String role : roles) {
            if (role.equals(principal.role())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权使用外部接口安全功能");
    }

    private String adapterConfigStatus(String... requiredValues) {
        if ("dev".equals(mode)) {
            return "ACTIVE";
        }
        for (String value : requiredValues) {
            if (!hasText(value)) {
                return "INCOMPLETE";
            }
        }
        return "ACTIVE";
    }

    private String adapterConfigStatusForProvider(String provider, String apiKey) {
        if ("dev".equals(provider)) {
            return "ACTIVE";
        }
        return hasText(apiKey) ? "ACTIVE" : "INCOMPLETE";
    }

    private List<SecurityCheckDraft> buildSecurityChecks() {
        List<SecurityCheckDraft> checks = new ArrayList<>();
        boolean devMode = "dev".equals(mode);
        checks.add(configCheck("WECHAT_PAY", "微信商户号", wechatMchId, devMode));
        checks.add(configCheck("WECHAT_PAY", "微信小程序/公众号 AppID", wechatAppId, devMode));
        checks.add(secretCheck("WECHAT_PAY", "微信回调验签密钥", wechatCallbackSecret, devMode));
        checks.add(configCheck("WECHAT_MESSAGE", "微信社区通知 AppID", wechatAppId, devMode));
        checks.add(configCheck("ALIPAY", "支付宝 AppID", alipayAppId, devMode));
        checks.add(configCheck("ALIPAY", "支付宝商户号", alipayMerchantId, devMode));
        checks.add(secretCheck("ALIPAY", "支付宝应用私钥", alipayPrivateKey, devMode));
        checks.add(secretCheck("ALIPAY", "支付宝平台公钥", alipayPublicKey, devMode));
        checks.add(secretCheck("ALIPAY", "支付宝回调验签密钥", alipayCallbackSecret, devMode));
        checks.add(configCheck("BANK_DIRECT", "银行直连地址", bankBaseUrl, devMode));
        checks.add(configCheck("BANK_DIRECT", "银行商户/机构号", bankMerchantId, devMode));
        checks.add(secretCheck("BANK_DIRECT", "银行回执验签密钥", bankCallbackSecret, devMode));
        checks.add(secretCheck("SMS", "短信 API Key", smsApiKey, "dev".equals(smsProvider)));
        checks.add(secretCheck("IDENTITY", "实名认证 API Key", identityApiKey, "dev".equals(identityProvider)));
        checks.add(secretStrengthCheck("SECURITY", "JWT 签名密钥", jwtSecret, "dev-only-change-me"));
        checks.add(secretStrengthCheck("SECURITY", "敏感字段加密密钥", encryptionKey, "dev-sensitive-key-change-me"));
        checks.add(new SecurityCheckDraft("SECURITY", "回调时间戳与 nonce 防重放", "PASS", "LOW", "支付回调与银行回执已接入时间戳窗口和 nonce 唯一校验"));
        checks.add(logMaskCheck());
        return checks;
    }

    private SecurityCheckDraft configCheck(String adapterCode, String item, String value, boolean allowDevBlank) {
        if (hasText(value)) {
            return new SecurityCheckDraft(adapterCode, item, "PASS", "LOW", "已配置：" + mask(value));
        }
        if (allowDevBlank) {
            return new SecurityCheckDraft(adapterCode, item, "WARN", "MEDIUM", "dev 模式允许空配置，生产需通过环境变量配置");
        }
        return new SecurityCheckDraft(adapterCode, item, "FAIL", "HIGH", "real 模式缺少必填配置");
    }

    private SecurityCheckDraft secretCheck(String adapterCode, String item, String value, boolean allowDevBlank) {
        if (hasText(value) && value.length() >= 12) {
            return new SecurityCheckDraft(adapterCode, item, "PASS", "LOW", "密钥已配置，长度 " + value.length() + "，展示值：" + mask(value));
        }
        if (allowDevBlank) {
            return new SecurityCheckDraft(adapterCode, item, "WARN", "MEDIUM", "dev 模式使用模拟适配器，生产需配置真实密钥");
        }
        return new SecurityCheckDraft(adapterCode, item, "FAIL", "HIGH", "real 模式缺少密钥或密钥长度不足");
    }

    private SecurityCheckDraft secretStrengthCheck(String adapterCode, String item, String value, String defaultValue) {
        if (!hasText(value) || value.equals(defaultValue)) {
            return new SecurityCheckDraft(adapterCode, item, "WARN", "HIGH", "仍使用默认开发密钥，生产必须替换");
        }
        if (value.length() < 16) {
            return new SecurityCheckDraft(adapterCode, item, "WARN", "MEDIUM", "密钥长度偏短，建议不少于 16 位");
        }
        return new SecurityCheckDraft(adapterCode, item, "PASS", "LOW", "密钥已替换，长度 " + value.length());
    }

    private SecurityCheckDraft logMaskCheck() {
        List<IntegrationCallLogSummary> logs = jdbc.sql("""
            select request_summary requestSummary, response_summary responseSummary
            from integration_call_log
            order by created_at desc limit 300
            """).query(IntegrationCallLogSummary.class).list();
        int riskyLogs = 0;
        for (IntegrationCallLogSummary log : logs) {
            String text = (log.requestSummary() == null ? "" : log.requestSummary()) + " " + (log.responseSummary() == null ? "" : log.responseSummary());
            if (text.matches(".*(?<![0-9])[0-9]{17}[0-9Xx](?![0-9]).*") || text.matches(".*(?<![0-9])1[3-9][0-9]{9}(?![0-9]).*")) {
                riskyLogs++;
            }
        }
        if (riskyLogs > 0) {
            return new SecurityCheckDraft("SECURITY", "外部调用日志脱敏", "WARN", "HIGH", "发现 " + riskyLogs + " 条疑似明文敏感日志");
        }
        return new SecurityCheckDraft("SECURITY", "外部调用日志脱敏", "PASS", "LOW", "未发现手机号或身份证号明文模式");
    }

    private String remediationAction(SecurityCheckDraft check) {
        if ("PASS".equals(check.checkResult())) {
            return "无需整改，保持配置和审计留痕";
        }
        if ("JWT 签名密钥".equals(check.checkItem())) {
            return "设置生产 JWT_SECRET，长度不少于 16 位，并重启服务后重新体检";
        }
        if ("敏感字段加密密钥".equals(check.checkItem())) {
            return "设置生产 SENSITIVE_ENCRYPTION_KEY，完成历史敏感字段密文兼容验证";
        }
        if ("外部调用日志脱敏".equals(check.checkItem())) {
            return "清理明文手机号/身份证日志，确保适配器请求响应摘要只保留脱敏值";
        }
        if (check.adapterCode().startsWith("WECHAT")) {
            return "补齐 WECHAT_MCH_ID、WECHAT_APP_ID、证书/回调验签配置，并执行支付回调联调";
        }
        if ("ALIPAY".equals(check.adapterCode())) {
            return "补齐 ALIPAY_APP_ID、ALIPAY_MERCHANT_ID、应用私钥、平台公钥，并执行退款联调";
        }
        if ("BANK_DIRECT".equals(check.adapterCode())) {
            return "补齐 BANK_BASE_URL、BANK_MERCHANT_ID、银行专线白名单和放款回执联调";
        }
        if ("SMS".equals(check.adapterCode())) {
            return "补齐 SMS_PROVIDER 与 SMS_API_KEY，发送缴费/审批/预警模板测试";
        }
        if ("IDENTITY".equals(check.adapterCode())) {
            return "补齐 IDENTITY_PROVIDER 与 IDENTITY_API_KEY，完成业主实名房屋绑定核验";
        }
        return "按风险等级完成配置整改后重新运行外部接口安全体检";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 6) {
            return "***";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 3);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskIdentity(String identityNo) {
        if (identityNo == null || identityNo.length() < 8) {
            return identityNo == null ? "" : identityNo;
        }
        return identityNo.substring(0, 4) + "**********" + identityNo.substring(identityNo.length() - 4);
    }

    private List<DatabaseCompatibilityItem> buildDatabaseCompatibilityItems(String currentVendor) {
        return List.of(
            databaseItem("MYSQL", "MySQL 8", "jdbc:mysql://", "com.mysql.cj.jdbc.Driver", "MySQL", "limit/now()/auto_increment 原生支持", "CURRENT".equals(statusForVendor(currentVendor, "MYSQL")) ? "PASS" : "PASS", "本地默认运行库，Flyway 脚本已验证"),
            databaseItem("DM", "达梦 DM8", "jdbc:dm://", "dm.jdbc.driver.DmDriver", "DM MySQL 兼容模式", "需验证 limit、now()、auto_increment/identity 兼容", currentVendor.equals("DM") ? "PASS" : "WARN", "引入达梦 JDBC Driver，设置 DB_URL=jdbc:dm://...，执行 Flyway dry-run 后处理自增与时间函数"),
            databaseItem("KINGBASE", "人大金仓 KingbaseES", "jdbc:kingbase8://", "com.kingbase8.Driver", "Kingbase MySQL/PostgreSQL 兼容模式", "需验证分页、布尔/时间类型、唯一键语法", currentVendor.equals("KINGBASE") ? "PASS" : "WARN", "引入 Kingbase JDBC Driver，设置 DB_URL=jdbc:kingbase8://...，按兼容模式调整迁移脚本"),
            databaseItem("OPENGAUSS", "openGauss/鲲鹏生态", "jdbc:opengauss://", "org.opengauss.Driver", "PostgreSQL 方言", "需改造 auto_increment、on duplicate key、datetime", currentVendor.equals("OPENGAUSS") ? "PASS" : "WARN", "作为信创扩展目标预留，需新增 PostgreSQL 方言迁移脚本")
        );
    }

    private DatabaseCompatibilityItem databaseItem(String vendorCode, String vendorName, String jdbcPrefix, String driverClass,
                                                   String flywayDialect, String sqlCompatibility, String checkResult,
                                                   String migrationAction) {
        return new DatabaseCompatibilityItem(
            vendorCode,
            vendorName,
            datasourceUrl != null && datasourceUrl.startsWith(jdbcPrefix) ? "CURRENT" : "READY",
            checkResult,
            jdbcPrefix,
            driverClass,
            flywayDialect,
            sqlCompatibility,
            migrationAction
        );
    }

    private String detectDatabaseVendor(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "UNKNOWN";
        }
        String normalized = jdbcUrl.toLowerCase();
        if (normalized.startsWith("jdbc:mysql:")) {
            return "MYSQL";
        }
        if (normalized.startsWith("jdbc:dm:")) {
            return "DM";
        }
        if (normalized.startsWith("jdbc:kingbase8:")) {
            return "KINGBASE";
        }
        if (normalized.startsWith("jdbc:opengauss:")) {
            return "OPENGAUSS";
        }
        return "UNKNOWN";
    }

    private String statusForVendor(String currentVendor, String vendor) {
        return vendor.equals(currentVendor) ? "CURRENT" : "READY";
    }

    private String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        return jdbcUrl.replaceAll("(?i)(password=)[^&;]+", "$1***")
            .replaceAll("(?i)(user=)[^&;]+", "$1***");
    }

    private List<DeploymentReadinessItem> buildDeploymentReadinessItems() {
        List<DeploymentReadinessItem> items = new ArrayList<>();
        boolean databaseReady = databasePing();
        items.add(new DeploymentReadinessItem(
            "MySQL/Flyway 数据库",
            databaseReady ? "PASS" : "FAIL",
            true,
            maskJdbcUrl(datasourceUrl),
            databaseReady ? "应用可执行 select 1，Flyway 已随应用启动校验" : "应用无法执行数据库探测 SQL",
            "确认 deploy/docker-compose.yml 中 MySQL 容器健康，DB_URL/DB_USERNAME/DB_PASSWORD 指向可访问实例"
        ));
        boolean redisReady = tcpReachable(redisHost, redisPort);
        items.add(new DeploymentReadinessItem(
            "Redis 缓存/会话预留",
            redisReady ? "PASS" : "WARN",
            false,
            redisHost + ":" + redisPort,
            redisReady ? "TCP 端口可连接" : "未检测到 Redis 端口，当前版本可不用 Redis 运行",
            "如启用缓存、队列或分布式会话，先执行 docker compose up -d redis 并配置 REDIS_HOST/REDIS_PORT"
        ));
        Endpoint minio = parseEndpoint(minioEndpoint, 9000);
        boolean minioReady = tcpReachable(minio.host(), minio.port());
        items.add(new DeploymentReadinessItem(
            "MinIO 附件存储预留",
            minioReady ? "PASS" : "WARN",
            false,
            minioEndpoint,
            minioReady ? "对象存储端口可连接" : "未检测到 MinIO API 端口，支出附件当前使用资料 URL 台账",
            "如启用发票/合同附件上传，先执行 docker compose up -d minio 并配置 MINIO_ENDPOINT/账号密钥"
        ));
        Endpoint minioConsole = parseEndpoint(minioConsoleEndpoint, 9001);
        boolean consoleReady = tcpReachable(minioConsole.host(), minioConsole.port());
        items.add(new DeploymentReadinessItem(
            "MinIO 控制台",
            consoleReady ? "PASS" : "WARN",
            false,
            minioConsoleEndpoint,
            consoleReady ? "控制台端口可连接" : "未检测到 MinIO 控制台端口",
            "如需运维查看桶和对象，开放 9001 或按环境映射控制台端口"
        ));
        items.add(new DeploymentReadinessItem(
            "Web/H5 CORS 来源配置",
            parseCsv(corsAllowedOrigins).isEmpty() ? "FAIL" : "PASS",
            true,
            String.join("; ", parseCsv(corsAllowedOrigins)),
            "WebConfig 与网关状态读取同一份 platform.cors.allowed-origins 配置",
            "生产环境设置 CORS_ALLOWED_ORIGINS 为实际管理端/业主端域名，多个来源用英文逗号分隔"
        ));
        return items;
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    private boolean databasePing() {
        try {
            Integer result = jdbc.sql("select 1").query(Integer.class).single();
            return result != null && result == 1;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Endpoint parseEndpoint(String endpoint, int defaultPort) {
        if (endpoint == null || endpoint.isBlank()) {
            return new Endpoint("localhost", defaultPort);
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? endpoint.replace("http://", "").replace("https://", "") : uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            return new Endpoint(host, port);
        } catch (Exception ignored) {
            return new Endpoint(endpoint, defaultPort);
        }
    }

    private int dataExchangeDomainCount(String domain, Long communityId) {
        String normalized = domain == null ? "" : domain.trim().toUpperCase();
        return switch (normalized) {
            case "BILLING" -> countDomain("""
                select count(*) from bill b
                join house h on h.id = b.house_id
                where (:communityId is null or h.community_id = :communityId)
                """, communityId);
            case "BANK_FLOW" -> countDomain("""
                select count(*) from bank_flow
                where (:communityId is null or community_id = :communityId)
                """, communityId);
            case "PUBLIC_REVENUE" -> countDomain("""
                select count(*) from expense_order
                where (:communityId is null or community_id = :communityId)
                """, communityId);
            case "RISK_ALERT" -> countDomain("""
                select count(*) from risk_alert
                where (:communityId is null or 1 = 1)
                """, communityId);
            case "CREDIT_SCORE" -> countDomain("""
                select count(*) from community_credit_score
                where (:communityId is null or community_id = :communityId)
                """, communityId);
            case "EXPORT_LOG" -> countDomain("""
                select count(*) from data_export_log
                where (:communityId is null or community_id = :communityId)
                """, communityId);
            default -> 0;
        };
    }

    private int countDomain(String sql, Long communityId) {
        return jdbc.sql(sql)
            .param("communityId", communityId)
            .query(Integer.class)
            .single();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void logDataExport(HttpServletRequest request, String exportModule, String fileName, String targetType, long targetId, int rowCount) {
        Object principalValue = request.getAttribute("principal");
        if (!(principalValue instanceof TokenService.Principal principal)) {
            return;
        }
        jdbc.sql("""
            insert into data_export_log(actor, role_code, tenant_id, community_id, export_module,
                                        target_type, target_id, file_name, row_count, data_scope, filter_summary, status, created_at)
            values(:actor, :roleCode, :tenantId, null, :exportModule,
                   :targetType, :targetId, :fileName, :rowCount, :dataScope, :filterSummary, 'SUCCESS', now())
            """)
            .param("actor", principal.username())
            .param("roleCode", principal.role())
            .param("tenantId", principal.tenantId())
            .param("exportModule", exportModule)
            .param("targetType", targetType)
            .param("targetId", targetId)
            .param("fileName", fileName)
            .param("rowCount", rowCount)
            .param("dataScope", "TENANT:" + principal.tenantId())
            .param("filterSummary", "target=" + targetType + "#" + targetId + "; community=NONE; rows=" + rowCount)
            .update();
    }

    private void logSensitiveFieldAudit(String targetTable, long targetId, String fieldName, String protection, String actor, String accessPurpose) {
        jdbc.sql("""
            insert into sensitive_field_audit(target_table, target_id, field_name, protection, actor, access_purpose, created_at)
            values(:targetTable, :targetId, :fieldName, :protection, :actor, :accessPurpose, now())
            """)
            .param("targetTable", targetTable)
            .param("targetId", targetId)
            .param("fieldName", fieldName)
            .param("protection", protection)
            .param("actor", actor)
            .param("accessPurpose", accessPurpose)
            .update();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record IntegrationConfig(long id, String adapterCode, String providerName, String mode, String endpointUrl, String merchantNo, String status, LocalDateTime updatedAt) {}
    public record IntegrationCallLog(long id, String adapterCode, String operation, String requestSummary, String responseSummary, String status, String traceNo, LocalDateTime createdAt) {}
    public record ExternalCallbackReceipt(long id, String adapterCode, String eventType, String businessNo, String signatureStatus, int httpStatus, String requestDigest, String responseSummary, String traceNo, String idempotencyKey, String processStatus, LocalDateTime receivedAt) {}
    public record ExternalCallbackNonce(long id, String adapterCode, String eventType, String businessNo, String nonceValue, long callbackTimestamp, LocalDateTime receivedAt) {}
    public record IntegrationSecurityCheck(long id, String adapterCode, String checkItem, String checkResult, String riskLevel, String evidence, LocalDateTime checkedAt) {}
    public record SensitiveFieldAudit(long id, String targetTable, long targetId, String fieldName, String protection, String actor, String accessPurpose, LocalDateTime createdAt) {}
    public record DatabaseCompatibility(String status, String currentVendor, String datasourceUrl, String datasourceUsername, List<DatabaseCompatibilityItem> items, String note) {}
    public record DatabaseCompatibilityItem(String vendorCode, String vendorName, String status, String checkResult, String jdbcPrefix, String driverClass, String flywayDialect, String sqlCompatibility, String migrationAction) {}
    public record DataExchangePackage(long id, String packageNo, String targetParty, String communityName, String dataDomains, int recordCount, String checksum, String status, String createdBy, LocalDateTime createdAt) {}
    public record DataExchangePackageRequest(String targetParty, Long communityId, List<String> dataDomains) {}
    public record DeploymentReadiness(String status, LocalDateTime checkedAt, List<DeploymentReadinessItem> items, String note) {}
    public record DeploymentReadinessItem(String component, String status, boolean required, String endpoint, String evidence, String action) {}
    public record AdapterConfigDraft(String adapterCode, String providerName, String mode, String endpointUrl, String merchantNo, String status) {}
    private record IntegrationCallLogSummary(String requestSummary, String responseSummary) {}
    private record SecurityCheckDraft(String adapterCode, String checkItem, String checkResult, String riskLevel, String evidence) {}
    private record Endpoint(String host, int port) {}
    public record SmsTestRequest(String phone, String content) {}
    public record IdentityTestRequest(String name, String phone, String identityNo, String expectedName, String expectedPhoneMask, String expectedIdentityMask) {}
    public record CallbackSignatureDiagnosticRequest(String provider, String eventType, String businessNo, Map<String, Object> payload, String signature) {}
}
