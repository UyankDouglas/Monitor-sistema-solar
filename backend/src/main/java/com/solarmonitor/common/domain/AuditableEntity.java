package com.solarmonitor.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Superclasse para entidades com colunas de auditoria {@code created_at} /
 * {@code updated_at}. Os valores são gerenciados pelo Hibernate — sem setter
 * público de propósito.
 */
@MappedSuperclass
@Getter
public abstract class AuditableEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
