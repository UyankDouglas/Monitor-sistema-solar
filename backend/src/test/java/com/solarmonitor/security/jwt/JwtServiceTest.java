package com.solarmonitor.security.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "segredo-de-teste-com-pelo-menos-32-bytes!";

    private final JwtService jwtService = new JwtService(SECRET, 15);

    @Test
    void roundTripPreservesClaims() {
        String token = jwtService.issueAccessToken(7L, "admin", List.of("ADMIN", "USER"), true);

        Claims claims = jwtService.parse(token).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get(JwtService.CLAIM_USER_ID, Long.class)).isEqualTo(7L);
        assertThat(claims.get(JwtService.CLAIM_ROLES, List.class)).containsExactly("ADMIN", "USER");
        assertThat(claims.get(JwtService.CLAIM_MUST_CHANGE_PASSWORD, Boolean.class)).isTrue();
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issueAccessToken(1L, "admin", List.of("ADMIN"), false);
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        assertThat(jwtService.parse(tampered)).isEmpty();
        assertThat(jwtService.parse("lixo.total.aqui")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService other = new JwtService("outro-segredo-completamente-diferente-32b!", 15);
        String foreign = other.issueAccessToken(1L, "admin", List.of("ADMIN"), false);

        assertThat(jwtService.parse(foreign)).isEmpty();
    }

    @Test
    void shortSecretIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new JwtService("curto", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
