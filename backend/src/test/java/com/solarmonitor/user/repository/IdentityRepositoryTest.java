package com.solarmonitor.user.repository;

import com.solarmonitor.AbstractRepositoryTest;
import com.solarmonitor.user.domain.RefreshToken;
import com.solarmonitor.user.domain.Role;
import com.solarmonitor.user.domain.RoleName;
import com.solarmonitor.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void rolesSeededByMigration() {
        assertThat(roleRepository.findByName(RoleName.ADMIN)).isPresent();
        assertThat(roleRepository.findByName(RoleName.USER)).isPresent();
    }

    @Test
    void adminUserSeededWithBothRoles() {
        Optional<User> admin = userRepository.findByUsername("admin");

        assertThat(admin).isPresent();
        assertThat(admin.get().getEmail()).isEqualTo("admin@solarmonitor.local");
        assertThat(admin.get().isEnabled()).isTrue();
        assertThat(admin.get().getPasswordHash()).startsWith("$2");
        assertThat(admin.get().getRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.ADMIN, RoleName.USER);
    }

    @Test
    void revokeAllIsVisibleToReloadInSameTransaction() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .user(admin)
                .tokenHash("hash-teste-revogacao")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        int revoked = refreshTokenRepository.revokeAllByUserId(admin.getId());

        assertThat(revoked).isEqualTo(1);
        // clearAutomatically garante que a releitura veja o estado do banco,
        // não a instância obsoleta (revoked=false) do contexto de persistência.
        assertThat(refreshTokenRepository.findByTokenHash("hash-teste-revogacao")
                .orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void duplicateUsernameIsRejected() {
        User duplicate = User.builder()
                .username("admin")
                .email("outro@solarmonitor.local")
                .passwordHash("$2b$10$hash-irrelevante-para-o-teste")
                .fullName("Duplicado")
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
