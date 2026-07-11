package com.solarmonitor.alert.repository;

import com.solarmonitor.alert.domain.Alert;
import com.solarmonitor.alert.domain.AlertStatus;
import com.solarmonitor.alert.domain.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Page<Alert> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    List<Alert> findAllByStatusOrderByTriggeredAtDesc(AlertStatus status);

    /**
     * Alerta ainda aberto de um tipo para um inversor — usado para
     * deduplicar: não reabrir INVERTER_OFFLINE se já existe um ativo.
     */
    Optional<Alert> findFirstByInverter_IdAndTypeAndStatusOrderByTriggeredAtDesc(
            Long inverterId, AlertType type, AlertStatus status);

    long countByStatus(AlertStatus status);
}
