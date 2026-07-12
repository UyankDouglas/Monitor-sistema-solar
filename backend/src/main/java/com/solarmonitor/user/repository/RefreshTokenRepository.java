package com.solarmonitor.user.repository;

import com.solarmonitor.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revogação condicional ATÔMICA — o coração da rotação segura: dois
     * refresh simultâneos com o mesmo token disputam este UPDATE e exatamente
     * UM recebe 1 linha afetada (rotaciona); o outro recebe 0 e cai no fluxo
     * de reuso. Sem isso, ambos leriam revoked=false e a detecção de roubo
     * nunca dispararia.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.tokenHash = :hash and t.revoked = false")
    int revokeIfActive(@Param("hash") String tokenHash);

    /**
     * Revoga todas as sessões ativas de um usuário (logout global).
     * clearAutomatically: bulk update não passa pelo contexto de persistência;
     * sem o clear, um token carregado antes continuaria com revoked=false na
     * mesma transação — falha de segurança silenciosa.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** Remove tokens expirados (housekeeping periódico). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
