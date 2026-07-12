package com.solarmonitor.alert.service;

import com.solarmonitor.alert.domain.Alert;
import com.solarmonitor.alert.domain.AlertStatus;
import com.solarmonitor.alert.repository.AlertRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Ciclo de vida dos alertas: ACTIVE → ACKNOWLEDGED → RESOLVED. O motor que
 * DISPARA alertas (avaliando as regras a cada ciclo) chega na etapa de
 * alertas; aqui está a gestão via API.
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional
    public Alert acknowledge(Long alertId) {
        Alert alert = find(alertId);
        if (alert.getStatus() != AlertStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Só alertas ATIVOS podem ser reconhecidos (atual: " + alert.getStatus() + ")");
        }
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(Instant.now());
        return alert;
    }

    @Transactional
    public Alert resolve(Long alertId) {
        Alert alert = find(alertId);
        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new IllegalArgumentException("Alerta " + alertId + " já está resolvido");
        }
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        return alert;
    }

    private Alert find(Long alertId) {
        return alertRepository.findById(alertId).orElseThrow(
                () -> new EntityNotFoundException("Alerta " + alertId + " não encontrado"));
    }
}
