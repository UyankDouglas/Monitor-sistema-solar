package com.solarmonitor.user.web;

import com.solarmonitor.security.web.AuthController.UserInfo;
import com.solarmonitor.user.domain.Role;
import com.solarmonitor.user.domain.RoleName;
import com.solarmonitor.user.domain.User;
import com.solarmonitor.user.repository.RefreshTokenRepository;
import com.solarmonitor.user.repository.RoleRepository;
import com.solarmonitor.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gestão de usuários — exclusiva de ADMIN (regra na SecurityConfig).
 * Usuários novos nascem com troca de senha obrigatória.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Gestão de usuários (apenas ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @Operation(summary = "Lista todos os usuários")
    @Transactional(readOnly = true)
    public List<UserInfo> list() {
        return userRepository.findAll().stream().map(UserInfo::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um usuário",
            description = "Nasce com troca de senha obrigatória no primeiro login.")
    @Transactional
    public UserInfo create(@RequestBody @Valid CreateUserRequest request) {
        // Normalização: unicidade é case-insensitive (índices lower() da V11)
        // e o login normaliza para lowercase — canonical é sempre minúsculo.
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' já existe");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("E-mail '" + email + "' já cadastrado");
        }
        Set<Role> roles = new HashSet<>();
        for (RoleName roleName : request.roles() == null || request.roles().isEmpty()
                ? List.of(RoleName.USER) : request.roles()) {
            roles.add(roleRepository.findByName(roleName).orElseThrow());
        }
        User user = User.builder()
                .username(username)
                .email(email)
                .fullName(request.fullName())
                .passwordHash(passwordEncoder.encode(request.temporaryPassword()))
                .mustChangePassword(true)
                .roles(roles)
                .build();
        return UserInfo.of(userRepository.save(user));
    }

    @PostMapping("/{id}/toggle-enabled")
    @Operation(summary = "Habilita/desabilita um usuário",
            description = "Desabilitar também revoga todas as sessões ativas.")
    @Transactional
    public UserInfo toggleEnabled(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException("Usuário " + id + " não encontrado"));
        if (user.getUsername().equals(authentication.getName())) {
            throw new IllegalArgumentException("Você não pode desabilitar a própria conta");
        }
        user.setEnabled(!user.isEnabled());
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeAllByUserId(user.getId());
        }
        return UserInfo.of(user);
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "[a-zA-Z0-9._-]+",
                    message = "Username: apenas letras, números, ponto, hífen e underscore")
            String username,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Size(min = 8, max = 100,
                    message = "Senha temporária deve ter entre 8 e 100 caracteres")
            String temporaryPassword,
            List<RoleName> roles) {
    }
}
