package com.dsyg.platform;

import com.dsyg.platform.auth.TokenService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {
    @Test
    void issuedTokenCanBeParsed() {
        TokenService tokenService = new TokenService("test-secret");
        String token = tokenService.issue("gov", "GOVERNMENT", 1);

        var principal = tokenService.parse(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().username()).isEqualTo("gov");
        assertThat(principal.get().role()).isEqualTo("GOVERNMENT");
        assertThat(principal.get().communityId()).isEqualTo(1);
    }
}
