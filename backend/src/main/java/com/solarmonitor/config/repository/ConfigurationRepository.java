package com.solarmonitor.config.repository;

import com.solarmonitor.config.domain.ConfigScope;
import com.solarmonitor.config.domain.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {

    Optional<Configuration> findByScopeAndKey(ConfigScope scope, String key);

    List<Configuration> findAllByScope(ConfigScope scope);

    Optional<Configuration> findByScopeAndInverter_IdAndKey(ConfigScope scope, Long inverterId, String key);

    Optional<Configuration> findByScopeAndPlant_IdAndKey(ConfigScope scope, Long plantId, String key);
}
