package com.solarmonitor.plant.repository;

import com.solarmonitor.plant.domain.Plant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantRepository extends JpaRepository<Plant, Long> {
}
