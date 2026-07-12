package com.solarmonitor.alert.web;

import com.solarmonitor.alert.domain.Alert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AlertDtoMapper {

    @Mapping(target = "inverterId", source = "inverter.id")
    AlertDto toDto(Alert alert);

    List<AlertDto> toDtos(List<Alert> alerts);
}
