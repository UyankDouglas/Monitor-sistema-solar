package com.solarmonitor.weather.repository;

import com.solarmonitor.weather.domain.Weather;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WeatherRepository extends JpaRepository<Weather, Long> {

    List<Weather> findAllByPlant_IdAndForecastAndObservedAtBetweenOrderByObservedAt(
            Long plantId, boolean forecast, Instant from, Instant to);

    Optional<Weather> findFirstByPlant_IdAndForecastFalseOrderByObservedAtDesc(Long plantId);
}
