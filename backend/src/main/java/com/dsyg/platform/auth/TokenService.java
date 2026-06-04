package com.dsyg.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class TokenService {
    private final String secret;

    public TokenService(@Value("${platform.jwt-secret}") String secret) {
        this.secret = secret;
    }

    public String issue(String username, String role, long communityId) {
        return issue(username, role, communityId, 0);
    }

    public String issue(String username, String role, long communityId, long tenantId) {
        String payload = username + "|" + role + "|" + communityId + "|" + tenantId + "|" + Instant.now().plusSeconds(86400).getEpochSecond();
        return b64(payload) + "." + sign(payload);
    }

    public Optional<Principal> parse(String token) {
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        if (!sign(payload).equals(parts[1])) {
            return Optional.empty();
        }
        String[] fields = payload.split("\\|");
        if (fields.length != 4 && fields.length != 5) {
            return Optional.empty();
        }
        long expiresAt = Long.parseLong(fields[fields.length - 1]);
        if (expiresAt < Instant.now().getEpochSecond()) {
            return Optional.empty();
        }
        long tenantId = fields.length == 5 ? Long.parseLong(fields[3]) : 0;
        return Optional.of(new Principal(fields[0], fields[1], Long.parseLong(fields[2]), tenantId));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return b64(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign token", ex);
        }
    }

    private String b64(String value) {
        return b64(value.getBytes(StandardCharsets.UTF_8));
    }

    private String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record Principal(String username, String role, long communityId, long tenantId) {}
}
