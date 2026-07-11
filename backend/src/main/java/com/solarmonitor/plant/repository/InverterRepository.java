package com.solarmonitor.plant.repository;

import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InverterRepository extends JpaRepository<Inverter, Long> {

    Optional<Inverter> findBySerialNumber(String serialNumber);

    List<Inverter> findAllByPlant_Id(Long plantId);

    List<Inverter> findAllByStatus(InverterStatus status);
}
