package com.solarmonitor.energy.repository;

import com.solarmonitor.energy.domain.DailyGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyGenerationRepository extends JpaRepository<DailyGeneration, Long> {

    Optional<DailyGeneration> findByInverter_IdAndGenerationDate(Long inverterId, LocalDate date);

    List<DailyGeneration> findAllByInverter_IdAndGenerationDateBetweenOrderByGenerationDate(
            Long inverterId, LocalDate from, LocalDate to);

    /** Melhor dia do inversor (estatísticas). */
    Optional<DailyGeneration> findFirstByInverter_IdOrderByEnergyKwhDesc(Long inverterId);

    /** Pior dia com geração registrada (> 0), ignorando dias sem dados. */
    Optional<DailyGeneration> findFirstByInverter_IdAndEnergyKwhGreaterThanOrderByEnergyKwhAsc(
            Long inverterId, java.math.BigDecimal minExclusive);
}
