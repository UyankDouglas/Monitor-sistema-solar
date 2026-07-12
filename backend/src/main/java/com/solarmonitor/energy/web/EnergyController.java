package com.solarmonitor.energy.web;

import com.solarmonitor.common.web.InverterResolver;
import com.solarmonitor.energy.domain.DailyGeneration;
import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.domain.MpptReading;
import com.solarmonitor.energy.repository.DailyGenerationRepository;
import com.solarmonitor.energy.repository.EnergySampleRepository;
import com.solarmonitor.energy.repository.MonthlyGenerationRepository;
import com.solarmonitor.energy.repository.MpptReadingRepository;
import com.solarmonitor.energy.service.ExportService;
import com.solarmonitor.energy.service.HistoryService;
import com.solarmonitor.energy.web.dto.DailyGenerationDto;
import com.solarmonitor.energy.web.dto.EnergySeriesDto;
import com.solarmonitor.energy.web.dto.MonthlyGenerationDto;
import com.solarmonitor.plant.domain.Inverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de energia: leitura atual, série temporal, agregações diárias e
 * mensais e exportação (CSV/XLSX/PDF).
 */
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
@Tag(name = "Energia", description = "Leituras, histórico, agregações e exportação")
public class EnergyController {

    private final InverterResolver inverterResolver;
    private final EnergySampleRepository energySampleRepository;
    private final MpptReadingRepository mpptReadingRepository;
    private final DailyGenerationRepository dailyRepository;
    private final MonthlyGenerationRepository monthlyRepository;
    private final HistoryService historyService;
    private final ExportService exportService;
    private final EnergyDtoMapper mapper;

    @GetMapping("/current")
    @Operation(summary = "Última leitura persistida",
            description = "Sem inverterId, usa o primeiro inversor cadastrado. 404 se ainda não há amostras.")
    @Transactional(readOnly = true)
    public ResponseEntity<CurrentEnergyResponse> current(
            @RequestParam(name = "inverterId", required = false) Long inverterId) {
        Inverter inverter = inverterResolver.resolve(inverterId);
        return energySampleRepository.findFirstById_InverterIdOrderById_SampledAtDesc(inverter.getId())
                .map(sample -> ResponseEntity.ok(toResponse(inverter, sample)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    @Operation(summary = "Série temporal de potência/SOC",
            description = "Resolução automática: até 2 h retorna amostras brutas; períodos maiores "
                    + "são agregados no banco (time_bucket) mirando ~500 pontos.")
    public EnergySeriesDto history(
            @RequestParam(name = "inverterId", required = false) Long inverterId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Inverter inverter = inverterResolver.resolve(inverterId);
        return historyService.series(inverter.getId(), from, to);
    }

    @GetMapping("/daily")
    @Operation(summary = "Geração consolidada por dia no período [from, to]")
    @Transactional(readOnly = true)
    public List<DailyGenerationDto> daily(
            @RequestParam(name = "inverterId", required = false) Long inverterId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return mapper.toDailyDtos(dailyRows(inverterId, from, to));
    }

    @GetMapping("/monthly")
    @Operation(summary = "Geração consolidada por mês",
            description = "Com 'year', retorna os meses do ano; sem, todos os meses registrados.")
    @Transactional(readOnly = true)
    public List<MonthlyGenerationDto> monthly(
            @RequestParam(name = "inverterId", required = false) Long inverterId,
            @RequestParam(name = "year", required = false) Short year) {
        Inverter inverter = inverterResolver.resolve(inverterId);
        return mapper.toMonthlyDtos(year != null
                ? monthlyRepository.findAllByInverter_IdAndYearOrderByMonth(inverter.getId(), year)
                : monthlyRepository.findAllByInverter_IdOrderByYearAscMonthAsc(inverter.getId()));
    }

    @GetMapping("/daily/export")
    @Operation(summary = "Exporta a geração diária do período (csv, xlsx ou pdf)")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportDaily(
            @RequestParam(name = "inverterId", required = false) Long inverterId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "format", defaultValue = "csv") String format) {
        List<DailyGenerationDto> days = mapper.toDailyDtos(dailyRows(inverterId, from, to));
        String filename = "geracao-diaria_" + from + "_" + to;
        return switch (format.toLowerCase()) {
            case "csv" -> file(exportService.toCsv(days), filename + ".csv",
                    MediaType.parseMediaType("text/csv;charset=UTF-8"));
            case "xlsx" -> file(exportService.toXlsx(days), filename + ".xlsx",
                    MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            case "pdf" -> file(exportService.toPdf(days,
                            "Geração diária — " + from + " a " + to),
                    filename + ".pdf", MediaType.APPLICATION_PDF);
            default -> throw new IllegalArgumentException(
                    "Formato inválido: '" + format + "' (use csv, xlsx ou pdf)");
        };
    }

    private List<DailyGeneration> dailyRows(Long inverterId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Período inválido: 'from' deve ser anterior ou igual a 'to'");
        }
        Inverter inverter = inverterResolver.resolve(inverterId);
        return dailyRepository.findAllByInverter_IdAndGenerationDateBetweenOrderByGenerationDate(
                inverter.getId(), from, to);
    }

    private ResponseEntity<byte[]> file(byte[] content, String filename, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(type)
                .body(content);
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
