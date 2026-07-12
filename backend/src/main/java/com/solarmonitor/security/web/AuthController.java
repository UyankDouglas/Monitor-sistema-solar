package com.solarmonitor.security.web;

import com.solarmonitor.security.AuthService;
import com.solarmonitor.user.domain.Role;
import com.solarmonitor.user.domain.User;
import com.solarmonitor.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Login, refresh rotativo, logout, troca de senha e dados do usuário atual.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login com JWT + refresh token rotativo")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    @Operation(summary = "Autentica e emite access + refresh token")
    @SecurityRequirements // público: sem cadeado no Swagger
    public AuthResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest http) {
        var pair = authService.login(request.username(), request.password(),
                http.getHeader("User-Agent"), clientIp(http));
        return AuthResponse.of(pair);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Troca o refresh token por um novo par (rotação)",
            description = "Reuso de token já rotacionado revoga TODAS as sessões do usuário.")
    @SecurityRequirements
    public AuthResponse refresh(@RequestBody @Valid RefreshRequest request, HttpServletRequest http) {
        var pair = authService.refresh(request.refreshToken(),
                http.getHeader("User-Agent"), clientIp(http));
        return AuthResponse.of(pair);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoga o refresh token da sessão",
            description = "Público de propósito: logout com access token expirado ainda precisa "
                    + "conseguir revogar o refresh token — a posse do próprio token é a autorização.")
    @SecurityRequirements
    public void logout(@RequestBody @Valid RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Troca a senha do usuário autenticado",
            description = "Revoga as demais sessões e emite um par novo de tokens.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public AuthResponse changePassword(@RequestBody @Valid ChangePasswordRequest request,
                                       Authentication authentication,
                                       HttpServletRequest http) {
        var pair = authService.changePassword(authentication.getName(),
                request.currentPassword(), request.newPassword(),
                http.getHeader("User-Agent"), clientIp(http));
        return AuthResponse.of(pair);
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @Transactional(readOnly = true)
    public UserInfo me(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return UserInfo.of(user);
    }

    /** Credencial inválida → 401 uniforme (sem revelar qual campo falhou). */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Não autorizado");
        problem.setDetail(e.getMessage());
        return problem;
    }

    /**
     * IP para auditoria de sessões. Atrás do nginx do projeto
     * ({@code proxy_add_x_forwarded_for} APENAS ANEXA), o IP confiável é o
     * X-Real-IP (setado pelo nginx) ou o ÚLTIMO elemento do X-Forwarded-For —
     * o primeiro é controlado pelo cliente. Em acesso direto à porta 8080 o
     * header segue spoofável; o valor é auditoria, nunca autorização.
     */
    private String clientIp(HttpServletRequest http) {
        String realIp = http.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.substring(forwarded.lastIndexOf(',') + 1).trim();
        }
        return http.getRemoteAddr();
    }

    // --- DTOs -----------------------------------------------------------

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100,
                    message = "A nova senha deve ter entre 8 e 100 caracteres")
            String newPassword) {
    }

    public record AuthResponse(String accessToken, long expiresInSeconds,
                               String refreshToken, UserInfo user) {
        public static AuthResponse of(AuthService.TokenPair pair) {
            return new AuthResponse(pair.accessToken(), pair.expiresInSeconds(),
                    pair.refreshToken(), UserInfo.of(pair.user()));
        }
    }

    public record UserInfo(Long id, String username, String fullName, String email,
                           List<String> roles, boolean mustChangePassword, boolean enabled) {
        public static UserInfo of(User user) {
            return new UserInfo(user.getId(), user.getUsername(), user.getFullName(),
                    user.getEmail(),
                    user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList(),
                    user.isMustChangePassword(),
                    user.isEnabled());
        }
    }
}
