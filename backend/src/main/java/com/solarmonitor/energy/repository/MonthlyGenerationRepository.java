package com.solarmonitor.energy.repository;

import com.solarmonitor.energy.domain.MonthlyGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyGenerationRepository extends JpaRepository<MonthlyGeneration, Long> {

    Optional<MonthlyGeneration> findByInverter_IdAndYearAndMonth(Long inverterId, Short year, Short month);

    List<MonthlyGeneration> findAllByInverter_IdAndYearOrderByMonth(Long inverterId, Short year);

    List<MonthlyGeneration> findAllByInverter_IdOrderByYearAscMonthAsc(Long inverterId);
}
