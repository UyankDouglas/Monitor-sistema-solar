package com.solarmonitor.security;

import com.solarmonitor.security.jwt.JwtService;
import com.solarmonitor.user.domain.RefreshToken;
import com.solarmonitor.user.domain.Role;
import com.solarmonitor.user.domain.User;
import com.solarmonitor.user.repository.RefreshTokenRepository;
import com.solarmonitor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Autenticação: login, rotação de refresh token, logout e troca de senha.
 *
 * <p>Refresh tokens: valor aleatório de 256 bits entregue UMA vez ao cliente;
 * o banco guarda apenas o SHA-256 (vazamento do banco não permite reuso).
 * Rotação: cada refresh revoga o token usado e emite outro. <strong>Detecção
 * de reuso</strong>: se um token JÁ revogado for apresentado, todas as
 * sessões do usuário são revogadas — o cenário típico é um token roubado
 * sendo usado depois do legítimo.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Hash BCrypt de valor aleatório descartado, usado para equalizar o tempo
     * de resposta quando o username não existe — sem ele, o login de usuário
     * inexistente retornaria em ~2 ms contra ~80 ms do BCrypt real, permitindo
     * enumerar usernames por timing.
     */
    private static final String DUMMY_HASH =
            "$2b$10$z.uwgxAdf4QNHNi8zyHpuOOOacOFQMlzZEJ/0p0acmef5hoIg9mIy";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @org.springframework.beans.factory.annotation.Value("${app.security.jwt.refresh-ttl-days}")
    private long refreshTtlDays;

    public record TokenPair(String accessToken, long expiresInSeconds, String refreshToken, User user) {
    }

    @Transactional
    public TokenPair login(String username, String rawPassword, String userAgent, String ip) {
        String normalized = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByUsername(normalized).orElse(null);
        // O matches roda SEMPRE (hash dummy quando não há usuário) e antes da
        // checagem de enabled — tempo de resposta uniforme em todos os ramos.
        boolean matches = passwordEncoder.matches(rawPassword,
                user != null ? user.getPasswordHash() : DUMMY_HASH);
        if (user == null || !matches || !user.isEnabled()) {
            throw new BadCredentialsException("Usuário ou senha inválidos");
        }
        return issuePair(user, userAgent, ip);
    }

    /**
     * {@code noRollbackFor}: a revogação em massa da detecção de reuso PRECISA
     * ser commitada mesmo com a BadCredentialsException lançada em seguida —
     * com rollback, a "punição" seria silenciosamente desfeita e as sessões
     * roubadas continuariam válidas.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenPair refresh(String rawRefreshToken, String userAgent, String ip) {
        String hash = sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token desconhecido"));
        // Captura ANTES dos bulk updates: clearAutomatically desanexa a
        // entidade e lazy proxies explodiriam depois.
        Long userId = stored.getUser().getId();
        String username = stored.getUser().getUsername();
        Instant expiresAt = stored.getExpiresAt();

        // Rotação atômica: exatamente UMA requisição concorrente "vence" este
        // UPDATE condicional; as demais caem no fluxo de reuso abaixo.
        int rotated = refreshTokenRepository.revokeIfActive(hash);
        if (rotated == 0) {
            // Token já revogado (rotação anterior, logout ou corrida perdida):
            // possível token roubado — derruba todas as sessões do usuário.
            int revoked = refreshTokenRepository.revokeAllByUserId(userId);
            log.warn("REUSO de refresh token detectado para o usuário {}; {} sessões revogadas",
                    username, revoked);
            throw new BadCredentialsException("Refresh token reutilizado; faça login novamente");
        }
        if (expiresAt.isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expirado; faça login novamente");
        }
        User user = userRepository.findById(userId).orElseThrow(
                () -> new BadCredentialsException("Usuário não encontrado"));
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Usuário desabilitado");
        }
        return issuePair(user, userAgent, ip);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    /**
     * Troca de senha do usuário autenticado. Revoga todas as sessões (outros
     * dispositivos caem) e emite um par novo para o cliente atual.
     */
    @Transactional
    public TokenPair changePassword(String username, String currentPassword, String newPassword,
                                    String userAgent, String ip) {
        User user = userRepository.findByUsername(username).orElseThrow(
                () -> new BadCredentialsException("Usuário não encontrado"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Senha atual incorreta");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da atual");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("Senha alterada para o usuário {}", username);
        return issuePair(user, userAgent, ip);
    }

    private TokenPair issuePair(User user, String userAgent, String ip) {
        List<String> roles = user.getRoles().stream().map(Role::getName).map(Enum::name).toList();
        String access = jwtService.issueAccessToken(
                user.getId(), user.getUsername(), roles, user.isMustChangePassword());

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(refresh))
                .expiresAt(Instant.now().plus(Duration.ofDays(refreshTtlDays)))
                .userAgent(truncate(userAgent, 255))
                .ipAddress(truncate(ip, 45))
                .build());

        return new TokenPair(access, jwtService.accessTtlSeconds(), refresh, user);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM sem SHA-256", e);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
