package com.solarmonitor.energy.web;

import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.domain.MpptReading;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.repository.MpptReadingRepository;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.repository.InverterRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Consulta mínima da leitura corrente — permite validar a ingestão de ponta a
 * ponta já nesta etapa. A API REST completa (histórico, dashboard, filtros,
 * exportação) é a Etapa 5.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Energia", description = "Leituras de energia do(s) inversor(es)")
public class EnergyController {

    private final InverterRepository inverterRepository;
    private final EnergySampleRepository energySampleRepository;
    private final MpptReadingRepository mpptReadingRepository;

    @GetMapping("/api/energy/current")
    @Operation(summary = "Última leitura persistida",
            description = "Sem inverterId, usa o primeiro inversor cadastrado. 404 se ainda não há amostras.")
    @Transactional(readOnly = true)
    public ResponseEntity<CurrentEnergyResponse> current(
            @RequestParam(name = "inverterId", required = false) Long inverterId) {
        Inverter inverter = (inverterId != null
                ? inverterRepository.findById(inverterId)
                : inverterRepository.findAll().stream().findFirst())
                .orElse(null);
        if (inverter == null) {
            return ResponseEntity.notFound().build();
        }
        return energySampleRepository.findFirstById_InverterIdOrderById_SampledAtDesc(inverter.getId())
                .map(sample -> ResponseEntity.ok(toResponse(inverter, sample)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private CurrentEnergyResponse toResponse(Inverter inverter, EnergySample sample) {
        List<MpptReading> mppt = mpptReadingRepository
                .findAllById_InverterIdAndId_SampledAtOrderById_StringIndex(
                        inverter.getId(), sample.getId().getSampledAt());
        return new CurrentEnergyResponse(
                inverter.getId(),
                inverter.getName(),
                inverter.getStatus().name(),
                sample.getId().getSampledAt(),
                sample.getAcPowerW(),
                sample.getLoadPowerW(),
                sample.getExportPowerW(),
                sample.getImportPowerW(),
                sample.getBatteryPowerW(),
                sample.getBatterySocPct(),
                sample.getDailyEnergyKwh(),
                sample.getTotalEnergyKwh(),
                sample.getInverterTemperatureC(),
                mppt.stream().map(m -> new MpptResponse(
                        m.getId().getStringIndex(),
                        m.getVoltage(),
                        m.getCurrentA(),
                        m.getPowerW())).toList());
    }

    public record CurrentEnergyResponse(
            Long inverterId,
            String inverterName,
            String inverterStatus,
            Instant sampledAt,
            Integer acPowerW,
            Integer loadPowerW,
            Integer exportPowerW,
            Integer importPowerW,
            Integer batteryPowerW,
            BigDecimal batterySocPct,
            BigDecimal dailyEnergyKwh,
            BigDecimal totalEnergyKwh,
            BigDecimal inverterTemperatureC,
            List<MpptResponse> mppt) {
    }

    public record MpptResponse(Short stringIndex, BigDecimal voltage, BigDecimal currentA, Integer powerW) {
    }
}
