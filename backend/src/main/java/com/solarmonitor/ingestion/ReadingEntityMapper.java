package com.solarmonitor.ingestion;

import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.domain.EnergySampleId;
import com.solarmonitor.energy.domain.MpptReading;
import com.solarmonitor.energy.domain.MpptReadingId;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.provider.EnergyReading;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converte {@link EnergyReading} (contrato dos providers) nas entidades de
 * telemetria. Escrito à mão — e não via MapStruct — porque as chaves compostas
 * derivadas ({@code @EmbeddedId} + {@code @MapsId}) exigem construção
 * explícita que o gerador tornaria mais obscura, não menos. O MapStruct segue
 * sendo usado nos DTOs da API REST (Etapa 5).
 */
@Component
public class ReadingEntityMapper {

    public EnergySample toSample(EnergyReading reading, Inverter inverter) {
        return EnergySample.builder()
                .id(new EnergySampleId(null, reading.sampledAt()))
                .inverter(inverter)
                .acPowerW(reading.acPowerW())
                .loadPowerW(reading.loadPowerW())
                .exportPowerW(reading.exportPowerW())
                .importPowerW(reading.importPowerW())
                .batteryPowerW(reading.batteryPowerW())
                .dailyEnergyKwh(reading.dailyEnergyKwh())
                .monthlyEnergyKwh(reading.monthlyEnergyKwh())
                .totalEnergyKwh(reading.totalEnergyKwh())
                .gridVoltageL1(reading.gridVoltageL1())
                .gridVoltageL2(reading.gridVoltageL2())
                .gridVoltageL3(reading.gridVoltageL3())
                .gridCurrentA(reading.gridCurrentA())
                .gridFrequencyHz(reading.gridFrequencyHz())
                .batteryVoltage(reading.batteryVoltage())
                .batteryCurrentA(reading.batteryCurrentA())
                .batterySocPct(reading.batterySocPct())
                .batteryTemperatureC(reading.batteryTemperatureC())
                .inverterTemperatureC(reading.inverterTemperatureC())
                .inverterStatus(reading.status() == null ? InverterStatus.UNKNOWN : reading.status())
                .build();
    }

    public List<MpptReading> toMpptReadings(EnergyReading reading, Inverter inverter) {
        if (reading.mpptReadings() == null) {
            return List.of();
        }
        return reading.mpptReadings().stream()
                .map(mppt -> MpptReading.builder()
                        .id(new MpptReadingId(null, reading.sampledAt(), mppt.stringIndex()))
                        .inverter(inverter)
                        .voltage(mppt.voltage())
                        .currentA(mppt.currentA())
                        .powerW(mppt.powerW())
                        .build())
                .toList();
    }
}
