package com.solarmonitor.energy.web;

import com.solarmonitor.energy.domain.DailyGeneration;
import com.solarmonitor.energy.domain.MonthlyGeneration;
import com.solarmonitor.energy.web.dto.DailyGenerationDto;
import com.solarmonitor.energy.web.dto.MonthlyGenerationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Entidades de agregação → DTOs da API (MapStruct, gerado em compilação). */
@Mapper(componentModel = "spring")
public interface EnergyDtoMapper {

    @Mapping(target = "date", source = "generationDate")
    DailyGenerationDto toDto(DailyGeneration entity);

    List<DailyGenerationDto> toDailyDtos(List<DailyGeneration> entities);

    MonthlyGenerationDto toDto(MonthlyGeneration entity);

    List<MonthlyGenerationDto> toMonthlyDtos(List<MonthlyGeneration> entities);
}
