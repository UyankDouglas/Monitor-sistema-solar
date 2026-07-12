package com.solarmonitor.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Emissão e validação dos access tokens JWT (HS256).
 *
 * <p>Claims: {@code sub} = username, {@code uid} = id do usuário,
 * {@code roles} = papéis, {@code mcp} = troca de senha pendente (quando
 * true, o restante da API responde 403 até a troca — ver JwtAuthFilter).</p>
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_MUST_CHANGE_PASSWORD = "mcp";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(@Value("${app.security.jwt.secret}") String secret,
                      @Value("${app.security.jwt.access-ttl-minutes}") long accessTtlMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret precisa de pelo menos 32 bytes (HS256)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    public String issueAccessToken(Long userId, String username, List<String> roles,
                                   boolean mustChangePassword) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_MUST_CHANGE_PASSWORD, mustChangePassword)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Claims do token, se assinatura e validade conferem; vazio caso contrário. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
