package com.solarmonitor.plant.web;

import com.solarmonitor.plant.domain.Inverter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InverterDtoMapper {

    @Mapping(target = "plantId", source = "plant.id")
    @Mapping(target = "plantName", source = "plant.name")
    @Mapping(target = "plantTimezone", source = "plant.timezone")
    @Mapping(target = "installedCapacityKwp", source = "plant.installedCapacityKwp")
    InverterDto toDto(Inverter inverter);

    List<InverterDto> toDtos(List<Inverter> inverters);
}
