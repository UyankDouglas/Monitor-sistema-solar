package com.solarmonitor.provider.local;

import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.provider.EnergyReading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapa de registradores Modbus (holding, função 0x03) do Deye SUN-10K-SG04LP3
 * (híbrido trifásico de baixa tensão) e a decodificação para {@link EnergyReading}.
 *
 * <p><strong>Fonte:</strong> mapeamentos mantidos pela comunidade (integrações
 * Home Assistant / Solarman para a família SG04LP3). Endereços e escalas devem
 * ser <em>calibrados contra o equipamento real</em> na primeira conexão — por
 * isso estão concentrados aqui, numa única classe, com um ponto de decodificação.</p>
 *
 * <p>Convenções de sinal adotadas (documentadas também em {@code EnergyReading}):</p>
 * <ul>
 *   <li>bateria: positivo = descarregando;</li>
 *   <li>rede (reg. 625): positivo = importando, negativo = exportando.</li>
 * </ul>
 */
final class DeyeRegisters {

    /** Faixas de leitura por ciclo (start inclusivo, count). Duas rodadas cobrem tudo. */
    static final int[][] READ_RANGES = {
            {500, 42},   // 500..541: status, energias diária/total, temperaturas
            {586, 94},   // 586..679: bateria, rede, carga e MPPTs
    };

    // --- Endereços ------------------------------------------------------
    static final int REG_RUNNING_STATUS = 500;     // 0 standby, 1 self-check, 2 normal, 3 alarm, 4 fault
    static final int REG_DAILY_PRODUCTION = 529;   // 0.1 kWh
    static final int REG_TOTAL_PRODUCTION_LOW = 534;  // 0.1 kWh, palavra baixa
    static final int REG_TOTAL_PRODUCTION_HIGH = 535; // 0.1 kWh, palavra alta
    static final int REG_INVERTER_TEMP = 541;      // (raw - 1000) * 0.1 °C
    static final int REG_BATTERY_TEMP = 586;       // (raw - 1000) * 0.1 °C
    static final int REG_BATTERY_VOLTAGE = 587;    // 0.01 V
    static final int REG_BATTERY_SOC = 588;        // 1 %
    static final int REG_BATTERY_POWER = 590;      // W, signed
    static final int REG_GRID_VOLTAGE_L1 = 598;    // 0.1 V
    static final int REG_GRID_VOLTAGE_L2 = 599;
    static final int REG_GRID_VOLTAGE_L3 = 600;
    static final int REG_GRID_FREQUENCY = 609;     // 0.01 Hz
    static final int REG_GRID_TOTAL_POWER = 625;   // W, signed (positivo = importando)
    static final int REG_INVERTER_OUTPUT_POWER = 636; // W, signed
    static final int REG_LOAD_TOTAL_POWER = 653;   // W
    static final int REG_PV1_POWER = 672;          // W
    static final int REG_PV2_POWER = 673;          // W
    static final int REG_PV1_VOLTAGE = 676;        // 0.1 V
    static final int REG_PV1_CURRENT = 677;        // 0.1 A
    static final int REG_PV2_VOLTAGE = 678;        // 0.1 V
    static final int REG_PV2_CURRENT = 679;        // 0.1 A

    private DeyeRegisters() {
    }

    /**
     * Decodifica o conjunto de registradores lidos (endereço → valor sem sinal)
     * em uma {@link EnergyReading}.
     */
    static EnergyReading decode(Map<Integer, Integer> regs, Instant sampledAt) {
        Integer gridPower = signed(regs.get(REG_GRID_TOTAL_POWER));

        List<EnergyReading.MpptStringReading> mppt = new ArrayList<>(2);
        mppt.add(new EnergyReading.MpptStringReading((short) 1,
                scaled(regs.get(REG_PV1_VOLTAGE), 1, 1),
                scaled(regs.get(REG_PV1_CURRENT), 1, 2),
                unsigned(regs.get(REG_PV1_POWER))));
        mppt.add(new EnergyReading.MpptStringReading((short) 2,
                scaled(regs.get(REG_PV2_VOLTAGE), 1, 1),
                scaled(regs.get(REG_PV2_CURRENT), 1, 2),
                unsigned(regs.get(REG_PV2_POWER))));

        return EnergyReading.builder()
                .sampledAt(sampledAt)
                .status(mapStatus(regs.get(REG_RUNNING_STATUS)))
                .acPowerW(signed(regs.get(REG_INVERTER_OUTPUT_POWER)))
                .loadPowerW(unsigned(regs.get(REG_LOAD_TOTAL_POWER)))
                .importPowerW(gridPower == null ? null : Math.max(gridPower, 0))
                .exportPowerW(gridPower == null ? null : Math.max(-gridPower, 0))
                .batteryPowerW(signed(regs.get(REG_BATTERY_POWER)))
                .dailyEnergyKwh(scaled(regs.get(REG_DAILY_PRODUCTION), 1, 3))
                .totalEnergyKwh(total32(regs.get(REG_TOTAL_PRODUCTION_LOW), regs.get(REG_TOTAL_PRODUCTION_HIGH)))
                .gridVoltageL1(scaled(regs.get(REG_GRID_VOLTAGE_L1), 1, 1))
                .gridVoltageL2(scaled(regs.get(REG_GRID_VOLTAGE_L2), 1, 1))
                .gridVoltageL3(scaled(regs.get(REG_GRID_VOLTAGE_L3), 1, 1))
                .gridFrequencyHz(scaled(regs.get(REG_GRID_FREQUENCY), 2, 2))
                .batteryVoltage(scaled(regs.get(REG_BATTERY_VOLTAGE), 2, 1))
                .batterySocPct(scaled(regs.get(REG_BATTERY_SOC), 0, 2))
                .batteryTemperatureC(temperature(regs.get(REG_BATTERY_TEMP)))
                .inverterTemperatureC(temperature(regs.get(REG_INVERTER_TEMP)))
                .mpptReadings(mppt)
                .build();
    }

    private static InverterStatus mapStatus(Integer raw) {
        if (raw == null) {
            return InverterStatus.UNKNOWN;
        }
        return switch (raw) {
            case 0, 1 -> InverterStatus.STANDBY;
            case 2 -> InverterStatus.ONLINE;
            case 3, 4 -> InverterStatus.FAULT;
            default -> InverterStatus.UNKNOWN;
        };
    }

    /** Valor com {@code decimalShift} casas movidas (raw * 10^-shift), com {@code scale} casas finais. */
    private static BigDecimal scaled(Integer raw, int decimalShift, int scale) {
        if (raw == null) {
            return null;
        }
        return BigDecimal.valueOf(raw).movePointLeft(decimalShift).setScale(scale, RoundingMode.HALF_UP);
    }

    /** Temperaturas Deye: (raw − 1000) × 0,1 °C. */
    private static BigDecimal temperature(Integer raw) {
        if (raw == null) {
            return null;
        }
        return BigDecimal.valueOf(raw - 1000L).movePointLeft(1).setScale(1, RoundingMode.HALF_UP);
    }

    /** Total de 32 bits, palavra baixa primeiro, escala 0,1 kWh. */
    private static BigDecimal total32(Integer low, Integer high) {
        if (low == null || high == null) {
            return null;
        }
        long raw = ((long) high << 16) | low;
        return BigDecimal.valueOf(raw).movePointLeft(1).setScale(3, RoundingMode.HALF_UP);
    }

    private static Integer unsigned(Integer raw) {
        return raw;
    }

    /** Registrador de 16 bits interpretado com sinal (complemento de dois). */
    private static Integer signed(Integer raw) {
        if (raw == null) {
            return null;
        }
        return (int) (short) raw.intValue();
    }
}
