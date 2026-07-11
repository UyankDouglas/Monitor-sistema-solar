package com.solarmonitor.alert.repository;

import com.solarmonitor.alert.domain.AlertRule;
import com.solarmonitor.alert.domain.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByType(AlertType type);

    List<AlertRule> findAllByEnabledTrue();
}
