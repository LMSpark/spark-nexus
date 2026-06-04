package com.dsyg.platform.modules;

import com.dsyg.platform.auth.AccessControlService;
import com.dsyg.platform.auth.TokenService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RegistrationController {
    private final JdbcClient jdbc;
    private final AccessControlService access;
    private final ObjectMapper objectMapper;
    private final SensitiveDataService sensitive;

    public RegistrationController(JdbcClient jdbc, AccessControlService access, ObjectMapper objectMapper, SensitiveDataService sensitive) {
        this.jdbc = jdbc;
        this.access = access;
        this.objectMapper = objectMapper;
        this.sensitive = sensitive;
    }

    @GetMapping("/tenants")
    public List<TenantRow> tenants(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
                select id, tenant_code tenantCode, tenant_name tenantName, tenant_type tenantType,
                       contact_name contactName, contact_phone contactPhone, status, certification_status certificationStatus,
                       created_at createdAt
                from tenant order by id
                """).query(TenantRow.class).list();
    }

    @GetMapping("/tenants/{tenantId}/users")
    public List<TenantUserRow> tenantUsers(@PathVariable long tenantId, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        assertTenantManageAccess(principal, tenantId);
        return jdbc.sql("""
                select u.id userId, u.username, u.display_name displayName, r.tenant_id tenantId,
                       t.tenant_name tenantName, r.role_code roleCode, r.status, r.is_default isDefault,
                       r.created_at createdAt
                from user_tenant_relation r
                join app_user u on u.id = r.user_id
                join tenant t on t.id = r.tenant_id
                where r.tenant_id = :tenantId
                order by r.is_default desc, u.id
                """)
            .param("tenantId", tenantId)
            .query(TenantUserRow.class)
            .list();
    }

    @PostMapping("/tenants/{tenantId}/users")
    public Map<String, Object> createTenantUser(@PathVariable long tenantId, @RequestBody TenantUserRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        assertTenantManageAccess(principal, tenantId);
        Integer exists = jdbc.sql("select count(*) from app_user where username = :username")
            .param("username", body.username())
            .query(Integer.class)
            .single();
        if (exists == 0) {
            jdbc.sql("""
                    insert into app_user(username, password_hash, display_name, role, community_id)
                    values(:username, :password, :displayName, :role, :communityId)
                    """)
                .param("username", body.username())
                .param("password", body.password() == null || body.password().isBlank() ? "admin123" : body.password())
                .param("displayName", body.displayName())
                .param("role", body.roleCode())
                .param("communityId", body.communityId() == null ? 1 : body.communityId())
                .update();
        }
        Long userId = jdbc.sql("select id from app_user where username = :username")
            .param("username", body.username())
            .query(Long.class)
            .single();
        jdbc.sql("""
                insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
                values(:userId, :tenantId, :roleCode, 'ACTIVE', :isDefault, now())
                on duplicate key update status = 'ACTIVE', is_default = values(is_default)
                """)
            .param("userId", userId)
            .param("tenantId", tenantId)
            .param("roleCode", body.roleCode())
            .param("isDefault", body.isDefault() == null ? 1 : body.isDefault())
            .update();
        audit(principal.username(), "创建/绑定租户用户-" + body.username(), "tenant", tenantId);
        return Map.of("userId", userId, "tenantId", tenantId, "status", "ACTIVE");
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/disable")
    public Map<String, Object> disableTenantUser(@PathVariable long tenantId, @PathVariable long userId, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        assertTenantManageAccess(principal, tenantId);
        jdbc.sql("update user_tenant_relation set status = 'DISABLED' where tenant_id = :tenantId and user_id = :userId")
            .param("tenantId", tenantId)
            .param("userId", userId)
            .update();
        audit(principal.username(), "停用租户用户", "tenant", tenantId);
        return Map.of("userId", userId, "tenantId", tenantId, "status", "DISABLED");
    }

    @PostMapping("/registrations/tenants")
    public Map<String, Object> registerTenant(@RequestBody TenantRegistrationRequest body) {
        String applicationNo = nextApplicationNo();
        String payload = """
            {"tenantType":"%s","tenantName":"%s","unifiedCreditCode":"%s","contactName":"%s","contactPhone":"%s","adminUsername":"%s","adminPassword":"%s","adminDisplayName":"%s"}
            """.formatted(safe(body.tenantType()), safe(body.tenantName()), safe(body.unifiedCreditCode()), safe(body.contactName()), safe(body.contactPhone()), safe(body.adminUsername()), safe(body.adminPassword()), safe(body.adminDisplayName())).trim();
        String publicPayload = """
            {"tenantType":"%s","tenantName":"%s","unifiedCreditCode":"%s","contactName":"%s","adminUsername":"%s","adminDisplayName":"%s"}
            """.formatted(safe(body.tenantType()), safe(body.tenantName()), safe(body.unifiedCreditCode()), safe(body.contactName()), safe(body.adminUsername()), safe(body.adminDisplayName())).trim();
        jdbc.sql("""
                insert into registration_application(application_no, applicant_type, applicant_name, applicant_phone, target_type,
                                                     target_id, payload_json, applicant_phone_cipher, payload_cipher, status, submitted_at)
                values(:applicationNo, :applicantType, :applicantName, :applicantPhone, 'TENANT',
                       null, :payload, :phoneCipher, :payloadCipher, 'PENDING', now())
                """)
            .param("applicationNo", applicationNo)
            .param("applicantType", body.tenantType())
            .param("applicantName", body.tenantName())
            .param("applicantPhone", sensitive.maskPhone(body.contactPhone()))
            .param("payload", publicPayload)
            .param("phoneCipher", sensitive.encrypt(body.contactPhone()))
            .param("payloadCipher", sensitive.encrypt(payload))
            .update();
        return Map.of("applicationNo", applicationNo, "status", "PENDING");
    }

    @PostMapping("/registrations/communities")
    public Map<String, Object> registerCommunity(@RequestBody CommunityRegistrationRequest body) {
        String applicationNo = nextApplicationNo();
        String payload = """
            {"district":"%s","street":"%s","neighborhood":"%s","name":"%s","households":%d}
            """.formatted(safe(body.district()), safe(body.street()), safe(body.neighborhood()), safe(body.name()), body.households()).trim();
        jdbc.sql("""
                insert into registration_application(application_no, applicant_type, applicant_name, applicant_phone, target_type,
                                                     target_id, payload_json, applicant_phone_cipher, payload_cipher, status, submitted_at)
                values(:applicationNo, 'COMMUNITY', :applicantName, :applicantPhone, 'COMMUNITY',
                       null, :payload, :phoneCipher, :payloadCipher, 'PENDING', now())
                """)
            .param("applicationNo", applicationNo)
            .param("applicantName", body.name())
            .param("applicantPhone", sensitive.maskPhone(body.contactPhone()))
            .param("payload", payload)
            .param("phoneCipher", sensitive.encrypt(body.contactPhone()))
            .param("payloadCipher", sensitive.encrypt(payload))
            .update();
        return Map.of("applicationNo", applicationNo, "status", "PENDING");
    }

    @PostMapping("/registrations/community-relations")
    public Map<String, Object> registerCommunityRelation(@RequestBody CommunityRelationApplicationRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        String applicationNo = nextApplicationNo();
        String payload = """
            {"tenantId":%d,"communityId":%d,"relationType":"%s","startDate":"%s","dataScopes":"%s","permissions":"%s"}
            """.formatted(body.tenantId(), body.communityId(), safe(body.relationType()), body.startDate() == null ? LocalDate.now() : body.startDate(), safe(String.join(",", body.dataScopes() == null ? List.of() : body.dataScopes())), safe(String.join(",", body.permissions() == null ? List.of("READ") : body.permissions()))).trim();
        jdbc.sql("""
                insert into registration_application(application_no, applicant_type, applicant_name, applicant_phone,
                                                     target_type, target_id, payload_json, status, submitted_at, owner_user_id)
                values(:applicationNo, 'COMMUNITY_RELATION', :applicantName, :phone,
                       'COMMUNITY_RELATION', :communityId, :payload, 'PENDING', now(),
                       (select id from app_user where username = :username))
                """)
            .param("applicationNo", applicationNo)
            .param("applicantName", principal.username())
            .param("phone", "")
            .param("communityId", body.communityId())
            .param("payload", payload)
            .param("username", principal.username())
            .update();
        return Map.of("applicationNo", applicationNo, "status", "PENDING");
    }

    @PostMapping("/registrations/bank-services")
    public Map<String, Object> registerBankService(@RequestBody BankServiceApplicationRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        String applicationNo = nextApplicationNo();
        String payload = """
            {"bankTenantId":%d,"communityId":%d,"serviceType":"%s","fundAccountId":%s,"merchantNo":"%s"}
            """.formatted(body.bankTenantId(), body.communityId(), safe(body.serviceType()), body.fundAccountId() == null ? "null" : body.fundAccountId().toString(), safe(body.merchantNo())).trim();
        jdbc.sql("""
                insert into registration_application(application_no, applicant_type, applicant_name, applicant_phone,
                                                     target_type, target_id, payload_json, status, submitted_at, owner_user_id)
                values(:applicationNo, 'BANK_SERVICE', :applicantName, :phone,
                       'BANK_SERVICE', :communityId, :payload, 'PENDING', now(),
                       (select id from app_user where username = :username))
                """)
            .param("applicationNo", applicationNo)
            .param("applicantName", principal.username())
            .param("phone", "")
            .param("communityId", body.communityId())
            .param("payload", payload)
            .param("username", principal.username())
            .update();
        return Map.of("applicationNo", applicationNo, "status", "PENDING");
    }

    @GetMapping("/registrations/pending")
    public List<RegistrationRow> pendingRegistrations(HttpServletRequest request) {
        access.assertTenantAdmin(principal(request));
        return jdbc.sql("""
                select id, application_no applicationNo, applicant_type applicantType, applicant_name applicantName,
                       applicant_phone applicantPhone, target_type targetType, target_id targetId, payload_json payloadJson,
                       status, submitted_at submittedAt, reviewed_by reviewedBy, reviewed_at reviewedAt, review_comment reviewComment
                from registration_application
                order by case status when 'PENDING' then 0 else 1 end, submitted_at desc
                """).query(RegistrationRow.class).list();
    }

    @GetMapping("/registrations/export")
    public ResponseEntity<byte[]> exportRegistrations(HttpServletRequest request) {
        List<RegistrationRow> rows = pendingRegistrations(request);
        String fileName = "registration-applications.csv";
        StringBuilder csv = new StringBuilder("申请编号,申请类型,申请名称,联系电话,目标类型,目标ID,状态,提交时间,审核人,审核时间,审核意见\n");
        for (RegistrationRow row : rows) {
            csv.append(csv(row.applicationNo())).append(',')
                .append(csv(row.applicantType())).append(',')
                .append(csv(row.applicantName())).append(',')
                .append(csv(row.applicantPhone())).append(',')
                .append(csv(row.targetType())).append(',')
                .append(row.targetId() == null ? "" : row.targetId()).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.submittedAt() == null ? "" : row.submittedAt().toString())).append(',')
                .append(csv(row.reviewedBy())).append(',')
                .append(csv(row.reviewedAt() == null ? "" : row.reviewedAt().toString())).append(',')
                .append(csv(row.reviewComment())).append('\n');
        }
        logDataExport(request, "REGISTRATION_APPLICATION", fileName, "registration_application", 0, null, rows.size());
        return csvResponse(fileName, csv.toString());
    }

    @PostMapping("/registrations/{id}/approve")
    public Map<String, Object> approveRegistration(@PathVariable long id, @RequestBody(required = false) ReviewRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertTenantAdmin(principal);
        RegistrationRow application = registration(id);
        Long targetId = application.targetId();
        if ("TENANT".equals(application.targetType()) && targetId == null) {
            targetId = createTenantFromApplication(application);
            createTenantAdminFromApplication(application, targetId);
        }
        if ("COMMUNITY".equals(application.targetType()) && targetId == null) {
            targetId = createCommunityFromApplication(application);
        }
        if ("RESIDENT_HOUSE".equals(application.targetType()) && targetId != null) {
            Map<String, Object> payload = registrationPayload(application);
            long userId = longValue(payload.get("userId"), extractLong(application.payloadJson(), "userId"));
            String phone = stringValue(payload.get("phone"), application.applicantPhone());
            String identityNo = stringValue(payload.get("identityNo"), "");
            approveHouseBindingApplication(id, userId, targetId, application.applicantName(), sensitive.maskPhone(phone), sensitive.encrypt(phone), sensitive.maskIdentity(identityNo), sensitive.encrypt(identityNo), principal.username(), body == null ? null : body.comment());
            return Map.of("id", id, "status", "APPROVED", "targetId", targetId);
        }
        if ("COMMUNITY_RELATION".equals(application.targetType())) {
            targetId = approveCommunityRelationApplication(application, principal.username(), body == null ? null : body.comment());
            return Map.of("id", id, "status", "APPROVED", "targetId", targetId);
        }
        if ("BANK_SERVICE".equals(application.targetType())) {
            targetId = approveBankServiceApplication(application, principal.username(), body == null ? null : body.comment());
            return Map.of("id", id, "status", "APPROVED", "targetId", targetId);
        }
        jdbc.sql("""
                update registration_application
                set status = 'APPROVED', target_id = :targetId, reviewed_by = :reviewedBy, reviewed_at = now(), review_comment = :comment
                where id = :id
                """)
            .param("targetId", targetId)
            .param("reviewedBy", principal.username())
            .param("comment", body == null || body.comment() == null ? "审核通过" : body.comment())
            .param("id", id)
            .update();
        audit(principal.username(), "审核通过注册申请-" + application.applicationNo(), "registration_application", id);
        return Map.of("id", id, "status", "APPROVED", "targetId", targetId == null ? 0 : targetId);
    }

    @PostMapping("/registrations/{id}/reject")
    public Map<String, Object> rejectRegistration(@PathVariable long id, @RequestBody(required = false) ReviewRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertTenantAdmin(principal);
        jdbc.sql("""
                update registration_application
                set status = 'REJECTED', reviewed_by = :reviewedBy, reviewed_at = now(), review_comment = :comment
                where id = :id
                """)
            .param("reviewedBy", principal.username())
            .param("comment", body == null || body.comment() == null ? "审核驳回" : body.comment())
            .param("id", id)
            .update();
        audit(principal.username(), "驳回注册申请", "registration_application", id);
        return Map.of("id", id, "status", "REJECTED");
    }

    @GetMapping("/communities/{communityId}/relations")
    public List<CommunityRelationRow> communityRelations(@PathVariable long communityId, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertCommunityAccess(principal, communityId, "COMMUNITY_PROFILE", "READ");
        return jdbc.sql("""
                select r.id, r.tenant_id tenantId, t.tenant_name tenantName, t.tenant_type tenantType,
                       r.community_id communityId, c.name communityName, r.relation_type relationType,
                       r.status, r.start_date startDate, r.end_date endDate, r.created_at createdAt
                from tenant_community_relation r
                join tenant t on t.id = r.tenant_id
                join community c on c.id = r.community_id
                where r.community_id = :communityId
                order by r.id
                """)
            .param("communityId", communityId)
            .query(CommunityRelationRow.class)
            .list();
    }

    @PostMapping("/communities/{communityId}/relations")
    public Map<String, Object> createCommunityRelation(@PathVariable long communityId, @RequestBody CommunityRelationRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertTenantAdmin(principal);
        jdbc.sql("""
                insert into tenant_community_relation(tenant_id, community_id, relation_type, status, start_date, end_date, created_at)
                values(:tenantId, :communityId, :relationType, 'ACTIVE', :startDate, null, now())
                """)
            .param("tenantId", body.tenantId())
            .param("communityId", communityId)
            .param("relationType", body.relationType())
            .param("startDate", body.startDate() == null ? LocalDate.now() : body.startDate())
            .update();
        audit(principal.username(), "绑定小区机构-" + body.relationType(), "community", communityId);
        return Map.of("communityId", communityId, "tenantId", body.tenantId(), "relationType", body.relationType());
    }

    @PostMapping("/communities/{communityId}/relations/{relationId}/disable")
    public Map<String, Object> disableCommunityRelation(@PathVariable long communityId, @PathVariable long relationId, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertTenantAdmin(principal);
        Long tenantId = jdbc.sql("""
                select tenant_id from tenant_community_relation
                where id = :id and community_id = :communityId
                """)
            .param("id", relationId)
            .param("communityId", communityId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "未找到小区机构关系"));
        jdbc.sql("update tenant_community_relation set status = 'DISABLED', end_date = current_date where id = :id and community_id = :communityId")
            .param("id", relationId)
            .param("communityId", communityId)
            .update();
        int revoked = jdbc.sql("""
                update data_authorization
                set status = 'REVOKED', effective_to = now()
                where grantee_tenant_id = :tenantId
                  and community_id = :communityId
                  and status = 'ACTIVE'
                """)
            .param("tenantId", tenantId)
            .param("communityId", communityId)
            .update();
        audit(principal.username(), "停用小区机构关系", "tenant_community_relation", relationId);
        audit(principal.username(), "停用关系同步撤销授权-" + revoked, "data_authorization", tenantId);
        return Map.of("relationId", relationId, "tenantId", tenantId, "status", "DISABLED", "revokedAuthorizations", revoked);
    }

    @PostMapping("/authorizations")
    public Map<String, Object> createAuthorization(@RequestBody AuthorizationRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertTenantAdmin(principal);
        for (String dataScope : body.dataScopes()) {
            for (String permission : body.permissions()) {
                jdbc.sql("""
                        insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope,
                                                       permission_code, status, effective_from, effective_to, created_at)
                        values(:grantorTenantId, :granteeTenantId, :communityId, :dataScope,
                               :permission, 'ACTIVE', now(), null, now())
                        """)
                    .param("grantorTenantId", body.grantorTenantId())
                    .param("granteeTenantId", body.granteeTenantId())
                    .param("communityId", body.communityId())
                    .param("dataScope", dataScope)
                    .param("permission", permission)
                    .update();
            }
        }
        audit(principal.username(), "新增数据授权", "community", body.communityId());
        return Map.of("communityId", body.communityId(), "status", "ACTIVE");
    }

    @PostMapping("/authorizations/{id}/revoke")
    public Map<String, Object> revokeAuthorization(@PathVariable long id, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertTenantAdmin(principal);
        jdbc.sql("update data_authorization set status = 'REVOKED', effective_to = now() where id = :id")
            .param("id", id)
            .update();
        audit(principal.username(), "撤销数据授权", "data_authorization", id);
        return Map.of("authorizationId", id, "status", "REVOKED");
    }

    @PostMapping("/resident/register")
    public Map<String, Object> registerResident(@RequestBody ResidentRegisterRequest body) {
        Integer exists = jdbc.sql("select count(*) from app_user where username = :username")
            .param("username", body.username())
            .query(Integer.class)
            .single();
        if (exists > 0) {
            throw new IllegalArgumentException("账号已存在");
        }
        jdbc.sql("""
                insert into app_user(username, password_hash, display_name, role, community_id)
                values(:username, :password, :displayName, 'OWNER', 1)
                """)
            .param("username", body.username())
            .param("password", body.password())
            .param("displayName", body.name())
            .update();
        Long userId = jdbc.sql("select id from app_user where username = :username")
            .param("username", body.username())
            .query(Long.class)
            .single();
        jdbc.sql("""
                insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
                values(:userId, 6, 'OWNER', 'ACTIVE', 1, now())
                """)
            .param("userId", userId)
            .update();
        return Map.of("userId", userId, "status", "REGISTERED");
    }

    @PostMapping("/resident/house-bindings")
    public Map<String, Object> submitHouseBinding(@RequestBody HouseBindingRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        Long userId = jdbc.sql("select id from app_user where username = :username")
            .param("username", principal.username())
            .query(Long.class)
            .single();
        HouseBindingCandidate house = jdbc.sql("""
                select h.id houseId, h.community_id communityId, c.name communityName, h.building,
                       h.unit_no unitNo, h.room_no roomNo
                from house h join community c on c.id = h.community_id
                where h.room_no = :roomNo
                  and (:building is null or h.building = :building)
                  and (:communityName is null or c.name = :communityName)
                order by h.id limit 1
                """)
            .param("roomNo", body.roomNo())
            .param("building", blankToNull(body.building()))
            .param("communityName", blankToNull(body.communityName()))
            .query(HouseBindingCandidate.class)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("未找到匹配房屋"));
        String applicationNo = nextApplicationNo();
        String payload = """
            {"userId":%d,"username":"%s","houseId":%d,"communityName":"%s","building":"%s","roomNo":"%s","residentType":"%s","name":"%s"}
            """.formatted(userId, safe(principal.username()), house.houseId(), safe(house.communityName()), safe(house.building()), safe(house.roomNo()), safe(body.residentType()), safe(body.name())).trim();
        String sensitivePayload = """
            {"userId":%d,"username":"%s","houseId":%d,"communityName":"%s","building":"%s","roomNo":"%s","residentType":"%s","name":"%s","phone":"%s","identityNo":"%s"}
            """.formatted(userId, safe(principal.username()), house.houseId(), safe(house.communityName()), safe(house.building()), safe(house.roomNo()), safe(body.residentType()), safe(body.name()), safe(body.phone()), safe(body.identityNo())).trim();
        jdbc.sql("""
                insert into registration_application(application_no, applicant_type, applicant_name, applicant_phone, target_type,
                                                     target_id, payload_json, applicant_phone_cipher, payload_cipher, status, submitted_at)
                values(:applicationNo, 'OWNER', :name, :phone, 'RESIDENT_HOUSE',
                       :houseId, :payload, :phoneCipher, :payloadCipher, 'PENDING', now())
                """)
            .param("applicationNo", applicationNo)
            .param("name", body.name())
            .param("phone", sensitive.maskPhone(body.phone()))
            .param("houseId", house.houseId())
            .param("payload", payload)
            .param("phoneCipher", sensitive.encrypt(body.phone()))
            .param("payloadCipher", sensitive.encrypt(sensitivePayload))
            .update();
        return Map.of("applicationNo", applicationNo, "status", "PENDING", "houseId", house.houseId());
    }

    @GetMapping("/resident/house-bindings/me")
    public List<ResidentHouseBindingRow> myHouseBindings(HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        return jdbc.sql("""
                select r.id, r.user_id userId, r.house_id houseId, c.name communityName, h.building, h.room_no roomNo,
                       r.resident_type residentType, r.verification_status verificationStatus, r.is_primary isPrimary,
                       r.start_date startDate, r.end_date endDate, r.created_at createdAt
                from resident_house_relation r
                join app_user u on u.id = r.user_id
                join house h on h.id = r.house_id
                join community c on c.id = h.community_id
                where u.username = :username
                order by r.is_primary desc, r.id desc
                """)
            .param("username", principal.username())
            .query(ResidentHouseBindingRow.class)
            .list();
    }

    @GetMapping("/resident/house-binding-applications/me")
    public List<RegistrationRow> myHouseBindingApplications(HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        return jdbc.sql("""
                select id, application_no applicationNo, applicant_type applicantType, applicant_name applicantName,
                       applicant_phone applicantPhone, target_type targetType, target_id targetId, payload_json payloadJson,
                       status, submitted_at submittedAt, reviewed_by reviewedBy, reviewed_at reviewedAt, review_comment reviewComment
                from registration_application
                where target_type = 'RESIDENT_HOUSE' and payload_json like :usernamePattern
                order by submitted_at desc
                """)
            .param("usernamePattern", "%\"username\":\"" + principal.username() + "\"%")
            .query(RegistrationRow.class)
            .list();
    }

    @PostMapping("/resident/house-bindings/{applicationId}/approve")
    public Map<String, Object> approveHouseBinding(@PathVariable long applicationId, @RequestBody(required = false) ReviewRequest body, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        access.assertTenantAdmin(principal);
        RegistrationRow application = registration(applicationId);
        if (!"RESIDENT_HOUSE".equals(application.targetType()) || application.targetId() == null) {
            throw new IllegalArgumentException("不是房屋绑定申请");
        }
        Map<String, Object> payload = registrationPayload(application);
        long userId = longValue(payload.get("userId"), extractLong(application.payloadJson(), "userId"));
        String phone = stringValue(payload.get("phone"), application.applicantPhone());
        String identityNo = stringValue(payload.get("identityNo"), "");
        approveHouseBindingApplication(applicationId, userId, application.targetId(), application.applicantName(), sensitive.maskPhone(phone), sensitive.encrypt(phone), sensitive.maskIdentity(identityNo), sensitive.encrypt(identityNo), principal.username(), body == null ? null : body.comment());
        return Map.of("applicationId", applicationId, "status", "APPROVED");
    }

    @PostMapping("/resident/house-bindings/{applicationId}/reject")
    public Map<String, Object> rejectHouseBinding(@PathVariable long applicationId, @RequestBody(required = false) ReviewRequest body, HttpServletRequest request) {
        return rejectRegistration(applicationId, body, request);
    }

    @PostMapping("/resident/house-bindings/{bindingId}/default")
    public Map<String, Object> setDefaultHouseBinding(@PathVariable long bindingId, HttpServletRequest request) {
        TokenService.Principal principal = principal(request);
        Long userId = jdbc.sql("select id from app_user where username = :username")
            .param("username", principal.username())
            .query(Long.class)
            .single();
        Long bindingUserId = jdbc.sql("select user_id from resident_house_relation where id = :id")
            .param("id", bindingId)
            .query(Long.class)
            .single();
        if (!bindingUserId.equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "无权设置其他人的默认房屋");
        }
        jdbc.sql("update resident_house_relation set is_primary = 0 where user_id = :userId")
            .param("userId", userId)
            .update();
        jdbc.sql("update resident_house_relation set is_primary = 1 where id = :id")
            .param("id", bindingId)
            .update();
        Long houseId = jdbc.sql("select house_id from resident_house_relation where id = :id")
            .param("id", bindingId)
            .query(Long.class)
            .single();
        Long communityId = jdbc.sql("select community_id from house where id = :houseId")
            .param("houseId", houseId)
            .query(Long.class)
            .single();
        jdbc.sql("update app_user set community_id = :communityId where id = :userId")
            .param("communityId", communityId)
            .param("userId", userId)
            .update();
        audit(principal.username(), "设置默认房屋", "resident_house_relation", bindingId);
        return Map.of("bindingId", bindingId, "status", "DEFAULT");
    }

    @GetMapping("/authorizations")
    public List<AuthorizationRow> authorizations(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        access.assertTenantAdmin(principal(request));
        return jdbc.sql("""
                select a.id, a.grantor_tenant_id grantorTenantId, gt.tenant_name grantorTenantName,
                       a.grantee_tenant_id granteeTenantId, rt.tenant_name granteeTenantName,
                       a.community_id communityId, c.name communityName, a.data_scope dataScope,
                       a.permission_code permissionCode, a.status, a.effective_from effectiveFrom, a.effective_to effectiveTo
                from data_authorization a
                join tenant gt on gt.id = a.grantor_tenant_id
                join tenant rt on rt.id = a.grantee_tenant_id
                join community c on c.id = a.community_id
                where (:communityId is null or a.community_id = :communityId)
                order by a.id desc
                """)
            .param("communityId", communityId)
            .query(AuthorizationRow.class)
            .list();
    }

    @GetMapping("/authorizations/export")
    public ResponseEntity<byte[]> exportAuthorizations(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        List<AuthorizationRow> rows = authorizations(communityId, request);
        String fileName = "data-authorizations.csv";
        StringBuilder csv = new StringBuilder("授权方,被授权方,小区,数据范围,权限,状态,生效时间,失效时间\n");
        for (AuthorizationRow row : rows) {
            csv.append(csv(row.grantorTenantName())).append(',')
                .append(csv(row.granteeTenantName())).append(',')
                .append(csv(row.communityName())).append(',')
                .append(csv(row.dataScope())).append(',')
                .append(csv(row.permissionCode())).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.effectiveFrom() == null ? "" : row.effectiveFrom().toString())).append(',')
                .append(csv(row.effectiveTo() == null ? "" : row.effectiveTo().toString())).append('\n');
        }
        logDataExport(request, "DATA_AUTHORIZATION", fileName, "data_authorization", communityId == null ? 0 : communityId, communityId, rows.size());
        return csvResponse(fileName, csv.toString());
    }

    private Long createTenantFromApplication(RegistrationRow application) {
        Map<String, Object> payload = registrationPayload(application);
        String code = "TENANT-" + application.applicationNo();
        String type = stringValue(payload.get("tenantType"), application.applicantType());
        String tenantName = stringValue(payload.get("tenantName"), application.applicantName());
        String contactPhone = stringValue(payload.get("contactPhone"), application.applicantPhone());
        jdbc.sql("""
                insert into tenant(tenant_code, tenant_name, tenant_type, parent_id, unified_credit_code, contact_name,
                                   contact_phone, contact_phone_cipher, status, certification_status, created_at, updated_at)
                values(:code, :name, :type, null, null, :contactName, :contactPhone, :contactPhoneCipher, 'ACTIVE', 'APPROVED', now(), now())
                """)
            .param("code", code)
            .param("name", tenantName)
            .param("type", type)
            .param("contactName", stringValue(payload.get("contactName"), application.applicantName()))
            .param("contactPhone", sensitive.maskPhone(contactPhone))
            .param("contactPhoneCipher", sensitive.encrypt(contactPhone))
            .update();
        return jdbc.sql("select id from tenant where tenant_code = :code")
            .param("code", code)
            .query(Long.class)
            .single();
    }

    private void createTenantAdminFromApplication(RegistrationRow application, long tenantId) {
        Map<String, Object> payload = registrationPayload(application);
        String type = stringValue(payload.get("tenantType"), application.applicantType());
        String adminUsername = stringValue(payload.get("adminUsername"), "");
        if (adminUsername.isBlank()) {
            adminUsername = "tenant_" + tenantId + "_admin";
        }
        String roleCode = switch (type) {
            case "BANK" -> "BANK";
            case "GOVERNMENT" -> "GOVERNMENT";
            case "COMMITTEE" -> "COMMITTEE";
            default -> "PROPERTY";
        };
        Integer exists = jdbc.sql("select count(*) from app_user where username = :username")
            .param("username", adminUsername)
            .query(Integer.class)
            .single();
        if (exists == 0) {
            jdbc.sql("""
                    insert into app_user(username, password_hash, display_name, role, community_id)
                    values(:username, :password, :displayName, :role, 1)
                    """)
                .param("username", adminUsername)
                .param("password", stringValue(payload.get("adminPassword"), "admin123"))
                .param("displayName", stringValue(payload.get("adminDisplayName"), stringValue(payload.get("contactName"), application.applicantName())))
                .param("role", roleCode)
                .update();
        }
        Long userId = jdbc.sql("select id from app_user where username = :username")
            .param("username", adminUsername)
            .query(Long.class)
            .single();
        jdbc.sql("""
                insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
                values(:userId, :tenantId, :roleCode, 'ACTIVE', 1, now())
                on duplicate key update status = 'ACTIVE', is_default = 1
                """)
            .param("userId", userId)
            .param("tenantId", tenantId)
            .param("roleCode", roleCode)
            .update();
    }

    private Long createCommunityFromApplication(RegistrationRow application) {
        Map<String, Object> payload = registrationPayload(application);
        jdbc.sql("""
                insert into community(district, street, neighborhood, name, households, occupancy_rate, approval_threshold)
                values(:district, :street, :neighborhood, :name, :households, 0, 30000.00)
                """)
            .param("district", stringValue(payload.get("district"), "待补充行政区"))
            .param("street", stringValue(payload.get("street"), "待补充街道"))
            .param("neighborhood", stringValue(payload.get("neighborhood"), "待补充社区"))
            .param("name", stringValue(payload.get("name"), application.applicantName()))
            .param("households", intValue(payload.get("households"), 0))
            .update();
        Long communityId = jdbc.sql("select max(id) from community").query(Long.class).single();
        jdbc.sql("""
                insert into approval_rule(community_id, expense_threshold, vote_threshold, approval_timeout_hours, vote_ratio, status, created_at)
                values(:communityId, 30000.00, 80000.00, 24, 66.67, 'ACTIVE', now())
                """)
            .param("communityId", communityId)
            .update();
        return communityId;
    }

    private void approveHouseBindingApplication(long applicationId, long userId, long houseId, String name, String phone, String phoneCipher, String identityMask, String identityCipher, String reviewer, String comment) {
        Integer approvedBindingCount = jdbc.sql("""
                select count(*) from resident_house_relation
                where user_id = :userId and verification_status = 'APPROVED'
                """)
            .param("userId", userId)
            .query(Integer.class)
            .single();
        boolean shouldSetPrimary = approvedBindingCount == 0;
        Integer exists = jdbc.sql("select count(*) from resident_house_relation where user_id = :userId and house_id = :houseId")
            .param("userId", userId)
            .param("houseId", houseId)
            .query(Integer.class)
            .single();
        if (exists == 0) {
            jdbc.sql("""
                    insert into resident_house_relation(user_id, house_id, resident_type, verification_status, is_primary, start_date, end_date, created_at)
                    values(:userId, :houseId, 'OWNER', 'APPROVED', :isPrimary, current_date, null, now())
                    """)
                .param("userId", userId)
                .param("houseId", houseId)
                .param("isPrimary", shouldSetPrimary ? 1 : 0)
                .update();
        }
        String username = jdbc.sql("select username from app_user where id = :userId")
            .param("userId", userId)
            .query(String.class)
            .single();
        Integer ownerExists = jdbc.sql("select count(*) from owner where username = :username and house_id = :houseId")
            .param("username", username)
            .param("houseId", houseId)
            .query(Integer.class)
            .single();
        if (ownerExists == 0) {
            jdbc.sql("""
                    insert into owner(username, house_id, name, phone, phone_cipher, identity_mask, identity_cipher)
                    values(:username, :houseId, :name, :phone, :phoneCipher, :identityMask, :identityCipher)
                    """)
                .param("username", username)
                .param("houseId", houseId)
                .param("name", name)
                .param("phone", phone)
                .param("phoneCipher", phoneCipher)
                .param("identityMask", identityMask)
                .param("identityCipher", identityCipher)
                .update();
        } else {
            jdbc.sql("""
                    update owner set house_id = :houseId, name = :name, phone = :phone,
                           phone_cipher = :phoneCipher, identity_mask = :identityMask, identity_cipher = :identityCipher
                    where username = :username and house_id = :houseId
                    """)
                .param("username", username)
                .param("houseId", houseId)
                .param("name", name)
                .param("phone", phone)
                .param("phoneCipher", phoneCipher)
                .param("identityMask", identityMask)
                .param("identityCipher", identityCipher)
                .update();
        }
        Long communityId = jdbc.sql("select community_id from house where id = :houseId")
            .param("houseId", houseId)
            .query(Long.class)
            .single();
        if (shouldSetPrimary) {
            jdbc.sql("update app_user set community_id = :communityId where id = :userId")
                .param("communityId", communityId)
                .param("userId", userId)
                .update();
        }
        jdbc.sql("""
                update registration_application
                set status = 'APPROVED', reviewed_by = :reviewer, reviewed_at = now(), review_comment = :comment
                where id = :id
                """)
            .param("reviewer", reviewer)
            .param("comment", comment == null ? "房屋绑定审核通过" : comment)
            .param("id", applicationId)
            .update();
        audit(reviewer, "审核通过住户房屋绑定", "registration_application", applicationId);
    }

    private Long approveCommunityRelationApplication(RegistrationRow application, String reviewer, String comment) {
        Map<String, Object> payload = registrationPayload(application);
        long tenantId = longValue(payload.get("tenantId"), 0);
        long communityId = longValue(payload.get("communityId"), application.targetId() == null ? 0 : application.targetId());
        String relationType = stringValue(payload.get("relationType"), "PROPERTY_SERVICE");
        LocalDate startDate = LocalDate.parse(stringValue(payload.get("startDate"), LocalDate.now().toString()));
        jdbc.sql("""
                insert into tenant_community_relation(tenant_id, community_id, relation_type, status, start_date, end_date, created_at)
                values(:tenantId, :communityId, :relationType, 'ACTIVE', :startDate, null, now())
                on duplicate key update status = 'ACTIVE', end_date = null
                """)
            .param("tenantId", tenantId)
            .param("communityId", communityId)
            .param("relationType", relationType)
            .param("startDate", startDate)
            .update();
        String[] scopes = stringValue(payload.get("dataScopes"), "COMMUNITY_PROFILE").split(",");
        String[] permissions = stringValue(payload.get("permissions"), "READ").split(",");
        grantScopes(1, tenantId, communityId, scopes, permissions);
        jdbc.sql("""
                update registration_application
                set status = 'APPROVED', reviewed_by = :reviewer, reviewed_at = now(), review_comment = :comment
                where id = :id
                """)
            .param("reviewer", reviewer)
            .param("comment", comment == null ? "小区服务关系审核通过" : comment)
            .param("id", application.id())
            .update();
        audit(reviewer, "审核通过小区服务关系", "registration_application", application.id());
        return communityId;
    }

    private Long approveBankServiceApplication(RegistrationRow application, String reviewer, String comment) {
        Map<String, Object> payload = registrationPayload(application);
        long bankTenantId = longValue(payload.get("bankTenantId"), 0);
        long communityId = longValue(payload.get("communityId"), application.targetId() == null ? 0 : application.targetId());
        String serviceType = stringValue(payload.get("serviceType"), "COLLECTION");
        Long fundAccountId = payload.get("fundAccountId") == null ? null : longValue(payload.get("fundAccountId"), 0);
        if (fundAccountId != null && fundAccountId == 0) {
            fundAccountId = null;
        }
        String merchantNo = stringValue(payload.get("merchantNo"), "MCH-" + communityId + "-" + bankTenantId);
        jdbc.sql("""
                insert into community_bank_config(community_id, bank_tenant_id, service_type, fund_account_id,
                                                  merchant_no, status, created_at, updated_at)
                values(:communityId, :bankTenantId, :serviceType, :fundAccountId,
                       :merchantNo, 'ACTIVE', now(), now())
                on duplicate key update status = 'ACTIVE', merchant_no = values(merchant_no), fund_account_id = values(fund_account_id), updated_at = now()
                """)
            .param("communityId", communityId)
            .param("bankTenantId", bankTenantId)
            .param("serviceType", serviceType)
            .param("fundAccountId", fundAccountId)
            .param("merchantNo", merchantNo)
            .update();
        jdbc.sql("""
                insert into tenant_community_relation(tenant_id, community_id, relation_type, status, start_date, end_date, created_at)
                values(:tenantId, :communityId, :relationType, 'ACTIVE', current_date, null, now())
                on duplicate key update status = 'ACTIVE', end_date = null
                """)
            .param("tenantId", bankTenantId)
            .param("communityId", communityId)
            .param("relationType", "COLLECTION".equals(serviceType) ? "BANK_COLLECTION" : "BANK_SUPERVISION")
            .update();
        grantScopes(1, bankTenantId, communityId, new String[]{"PAYMENT", "BANK_FLOW"}, new String[]{"READ", "WRITE"});
        jdbc.sql("""
                update registration_application
                set status = 'APPROVED', reviewed_by = :reviewer, reviewed_at = now(), review_comment = :comment
                where id = :id
                """)
            .param("reviewer", reviewer)
            .param("comment", comment == null ? "银行服务审核通过" : comment)
            .param("id", application.id())
            .update();
        Long configId = jdbc.sql("select id from community_bank_config where community_id = :communityId and bank_tenant_id = :bankTenantId and service_type = :serviceType")
            .param("communityId", communityId)
            .param("bankTenantId", bankTenantId)
            .param("serviceType", serviceType)
            .query(Long.class)
            .single();
        audit(reviewer, "审核通过银行服务", "community_bank_config", configId);
        return configId;
    }

    private void grantScopes(long grantorTenantId, long granteeTenantId, long communityId, String[] scopes, String[] permissions) {
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                continue;
            }
            for (String permission : permissions) {
                if (permission == null || permission.isBlank()) {
                    continue;
                }
                Integer exists = jdbc.sql("""
                        select count(*) from data_authorization
                        where grantee_tenant_id = :granteeTenantId and community_id = :communityId
                          and data_scope = :scope and permission_code = :permission and status = 'ACTIVE'
                        """)
                    .param("granteeTenantId", granteeTenantId)
                    .param("communityId", communityId)
                    .param("scope", scope.trim())
                    .param("permission", permission.trim())
                    .query(Integer.class)
                    .single();
                if (exists == 0) {
                    jdbc.sql("""
                            insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id,
                                                           data_scope, permission_code, status, effective_from, effective_to, created_at)
                            values(:grantorTenantId, :granteeTenantId, :communityId,
                                   :scope, :permission, 'ACTIVE', now(), null, now())
                            """)
                        .param("grantorTenantId", grantorTenantId)
                        .param("granteeTenantId", granteeTenantId)
                        .param("communityId", communityId)
                        .param("scope", scope.trim())
                        .param("permission", permission.trim())
                        .update();
                }
            }
        }
    }

    private RegistrationRow registration(long id) {
        return jdbc.sql("""
                select id, application_no applicationNo, applicant_type applicantType, applicant_name applicantName,
                       applicant_phone applicantPhone, target_type targetType, target_id targetId, payload_json payloadJson,
                       status, submitted_at submittedAt, reviewed_by reviewedBy, reviewed_at reviewedAt, review_comment reviewComment
                from registration_application where id = :id
                """)
            .param("id", id)
            .query(RegistrationRow.class)
            .single();
    }

    private String nextApplicationNo() {
        return "REG-" + System.currentTimeMillis();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private long extractLong(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("申请资料缺少字段：" + field);
        }
        start += marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("申请资料格式不正确");
        }
    }

    private Map<String, Object> registrationPayload(RegistrationRow application) {
        String cipher = jdbc.sql("select payload_cipher from registration_application where id = :id")
            .param("id", application.id())
            .query(String.class)
            .optional()
            .orElse(null);
        return parsePayload(sensitive.decryptOrDefault(cipher, application.payloadJson()));
    }

    private String stringValue(Object value, String fallback) {
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString();
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private long longValue(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if ("null".equalsIgnoreCase(value.toString())) {
            return fallback;
        }
        return Long.parseLong(value.toString());
    }

    private TokenService.Principal principal(HttpServletRequest request) {
        return (TokenService.Principal) request.getAttribute("principal");
    }

    private void assertTenantManageAccess(TokenService.Principal principal, long tenantId) {
        if (access.isPlatformAdmin(principal) || "GOVERNMENT".equals(principal.role()) || "STREET".equals(principal.role())) {
            return;
        }
        if (principal.tenantId() == tenantId) {
            return;
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "无权管理该机构用户");
    }

    private void requireAnyRole(HttpServletRequest request, String... roles) {
        TokenService.Principal principal = principal(request);
        for (String role : roles) {
            if (role.equals(principal.role())) {
                return;
            }
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "当前角色无权使用该功能");
    }

    private void audit(String actor, String action, String targetType, long targetId) {
        String previousHash = jdbc.sql("select hash from audit_log order by id desc limit 1")
            .query(String.class)
            .optional()
            .orElse("GENESIS");
        String hash = Integer.toHexString((actor + action + targetType + targetId + previousHash + LocalDateTime.now()).hashCode());
        jdbc.sql("""
                insert into audit_log(actor, action, target_type, target_id, hash, previous_hash, created_at)
                values(:actor, :action, :targetType, :targetId, :hash, :previousHash, now())
                """)
            .param("actor", actor)
            .param("action", action)
            .param("targetType", targetType)
            .param("targetId", targetId)
            .param("hash", hash)
            .param("previousHash", previousHash)
            .update();
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String content) {
        byte[] bytes = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void logDataExport(HttpServletRequest request, String exportModule, String fileName, String targetType, long targetId, Long communityId, int rowCount) {
        TokenService.Principal principal = principal(request);
        jdbc.sql("""
                insert into data_export_log(actor, role_code, tenant_id, community_id, export_module,
                                            target_type, target_id, file_name, row_count, data_scope, filter_summary, status, created_at)
                values(:actor, :roleCode, :tenantId, :communityId, :exportModule,
                       :targetType, :targetId, :fileName, :rowCount, :dataScope, :filterSummary, 'SUCCESS', now())
                """)
            .param("actor", principal.username())
            .param("roleCode", principal.role())
            .param("tenantId", principal.tenantId())
            .param("communityId", communityId)
            .param("exportModule", exportModule)
            .param("targetType", targetType)
            .param("targetId", targetId)
            .param("fileName", fileName)
            .param("rowCount", rowCount)
            .param("dataScope", exportDataScope(principal, communityId))
            .param("filterSummary", exportFilterSummary(targetType, targetId, communityId, rowCount))
            .update();
    }

    private String exportDataScope(TokenService.Principal principal, Long communityId) {
        if (communityId != null) {
            return "COMMUNITY:" + communityId;
        }
        if ("ADMIN".equals(principal.role()) || "GOVERNMENT".equals(principal.role()) || "STREET".equals(principal.role())) {
            return "AUTHORIZED_COMMUNITIES";
        }
        return "TENANT:" + principal.tenantId();
    }

    private String exportFilterSummary(String targetType, long targetId, Long communityId, int rowCount) {
        return "target=" + targetType + "#" + targetId
            + "; community=" + (communityId == null ? "AUTHORIZED" : communityId)
            + "; rows=" + rowCount;
    }

    public record TenantRegistrationRequest(String tenantType, String tenantName, String unifiedCreditCode, String contactName, String contactPhone, String adminUsername, String adminPassword, String adminDisplayName) {}
    public record CommunityRegistrationRequest(String district, String street, String neighborhood, String name, int households, String contactPhone) {}
    public record CommunityRelationApplicationRequest(long tenantId, long communityId, String relationType, LocalDate startDate, List<String> dataScopes, List<String> permissions) {}
    public record BankServiceApplicationRequest(long bankTenantId, long communityId, String serviceType, Long fundAccountId, String merchantNo) {}
    public record ResidentRegisterRequest(String username, String password, String name, String phone) {}
    public record HouseBindingRequest(String communityName, String building, String roomNo, String residentType, String name, String phone, String identityNo) {}
    public record TenantUserRequest(String username, String password, String displayName, String roleCode, Long communityId, Integer isDefault) {}
    public record ReviewRequest(String comment) {}
    public record CommunityRelationRequest(long tenantId, String relationType, LocalDate startDate) {}
    public record AuthorizationRequest(long grantorTenantId, long granteeTenantId, long communityId, List<String> dataScopes, List<String> permissions) {}
    public record TenantRow(long id, String tenantCode, String tenantName, String tenantType, String contactName, String contactPhone, String status, String certificationStatus, LocalDateTime createdAt) {}
    public record TenantUserRow(long userId, String username, String displayName, long tenantId, String tenantName, String roleCode, String status, int isDefault, LocalDateTime createdAt) {}
    public record RegistrationRow(long id, String applicationNo, String applicantType, String applicantName, String applicantPhone, String targetType, Long targetId, String payloadJson, String status, LocalDateTime submittedAt, String reviewedBy, LocalDateTime reviewedAt, String reviewComment) {}
    public record HouseBindingCandidate(long houseId, long communityId, String communityName, String building, String unitNo, String roomNo) {}
    public record ResidentHouseBindingRow(long id, long userId, long houseId, String communityName, String building, String roomNo, String residentType, String verificationStatus, int isPrimary, LocalDate startDate, LocalDate endDate, LocalDateTime createdAt) {}
    public record CommunityRelationRow(long id, long tenantId, String tenantName, String tenantType, long communityId, String communityName, String relationType, String status, LocalDate startDate, LocalDate endDate, LocalDateTime createdAt) {}
    public record AuthorizationRow(long id, long grantorTenantId, String grantorTenantName, long granteeTenantId, String granteeTenantName, long communityId, String communityName, String dataScope, String permissionCode, String status, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {}
}
