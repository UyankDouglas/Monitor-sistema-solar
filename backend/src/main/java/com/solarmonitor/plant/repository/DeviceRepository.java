package com.solarmonitor.plant.repository;

import com.solarmonitor.plant.domain.Device;
import com.solarmonitor.plant.domain.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findAllByInverter_Id(Long inverterId);

    List<Device> findAllByInverter_IdAndType(Long inverterId, DeviceType type);
}
