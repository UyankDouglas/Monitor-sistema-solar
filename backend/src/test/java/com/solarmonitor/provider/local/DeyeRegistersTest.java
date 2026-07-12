package com.solarmonitor.provider.local;

import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.provider.EnergyReading;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeyeRegistersTest {

    private final Instant now = Instant.parse("2026-07-11T15:00:00Z");

    @Test
    void decodesFullRegisterSetWithScalesAndSigns() {
        Map<Integer, Integer> regs = new HashMap<>();
        regs.put(DeyeRegisters.REG_RUNNING_STATUS, 2);          // normal → ONLINE
        regs.put(DeyeRegisters.REG_DAILY_PRODUCTION, 425);      // 42.5 kWh
        regs.put(DeyeRegisters.REG_TOTAL_PRODUCTION_LOW, 23046);
        regs.put(DeyeRegisters.REG_TOTAL_PRODUCTION_HIGH, 23);   // (23<<16)|23046 = 1.530.374 → 153.037,4 kWh
        regs.put(DeyeRegisters.REG_INVERTER_TEMP, 1475);        // 47.5 °C
        regs.put(DeyeRegisters.REG_BATTERY_TEMP, 1310);         // 31.0 °C
        regs.put(DeyeRegisters.REG_BATTERY_VOLTAGE, 5230);      // 52.3 V
        regs.put(DeyeRegisters.REG_BATTERY_SOC, 78);            // 78 %
        regs.put(DeyeRegisters.REG_BATTERY_POWER, 65136);       // -400 W (carregando)
        regs.put(DeyeRegisters.REG_GRID_VOLTAGE_L1, 2201);      // 220.1 V
        regs.put(DeyeRegisters.REG_GRID_VOLTAGE_L2, 2198);
        regs.put(DeyeRegisters.REG_GRID_VOLTAGE_L3, 2204);
        regs.put(DeyeRegisters.REG_GRID_FREQUENCY, 6002);       // 60.02 Hz
        regs.put(DeyeRegisters.REG_GRID_TOTAL_POWER, 62536);    // -3000 W → exportando
        regs.put(DeyeRegisters.REG_INVERTER_OUTPUT_POWER, 5200);
        regs.put(DeyeRegisters.REG_LOAD_TOTAL_POWER, 1800);
        regs.put(DeyeRegisters.REG_PV1_POWER, 2600);
        regs.put(DeyeRegisters.REG_PV2_POWER, 2650);
        regs.put(DeyeRegisters.REG_PV1_VOLTAGE, 3805);          // 380.5 V
        regs.put(DeyeRegisters.REG_PV1_CURRENT, 68);            // 6.8 A
        regs.put(DeyeRegisters.REG_PV2_VOLTAGE, 3790);
        regs.put(DeyeRegisters.REG_PV2_CURRENT, 70);

        EnergyReading reading = DeyeRegisters.decode(regs, now);

        assertThat(reading.sampledAt()).isEqualTo(now);
        assertThat(reading.status()).isEqualTo(InverterStatus.ONLINE);
        assertThat(reading.acPowerW()).isEqualTo(5200);
        assertThat(reading.loadPowerW()).isEqualTo(1800);
        assertThat(reading.batteryPowerW()).isEqualTo(-400);    // signed 16 bits
        assertThat(reading.exportPowerW()).isEqualTo(3000);     // rede negativa → exportação
        assertThat(reading.importPowerW()).isZero();
        assertThat(reading.dailyEnergyKwh()).isEqualByComparingTo("42.500");
        assertThat(reading.totalEnergyKwh()).isEqualByComparingTo("153037.400");
        assertThat(reading.inverterTemperatureC()).isEqualByComparingTo("47.5");
        assertThat(reading.batteryTemperatureC()).isEqualByComparingTo("31.0");
        assertThat(reading.batteryVoltage()).isEqualByComparingTo("52.3");
        assertThat(reading.batterySocPct()).isEqualByComparingTo("78.00");
        assertThat(reading.gridVoltageL1()).isEqualByComparingTo("220.1");
        assertThat(reading.gridFrequencyHz()).isEqualByComparingTo("60.02");
        assertThat(reading.mpptReadings()).hasSize(2);
        assertThat(reading.mpptReadings().get(0).powerW()).isEqualTo(2600);
        assertThat(reading.mpptReadings().get(0).voltage()).isEqualByComparingTo("380.5");
        assertThat(reading.mpptReadings().get(1).currentA()).isEqualByComparingTo("7.00");
    }

    @Test
    void importingFromGridWhenRegisterPositive() {
        Map<Integer, Integer> regs = new HashMap<>();
        regs.put(DeyeRegisters.REG_GRID_TOTAL_POWER, 1500);

        EnergyReading reading = DeyeRegisters.decode(regs, now);

        assertThat(reading.importPowerW()).isEqualTo(1500);
        assertThat(reading.exportPowerW()).isZero();
    }

    @Test
    void missingRegistersDecodeToNullsNotZeros() {
        EnergyReading reading = DeyeRegisters.decode(Map.of(), now);

        assertThat(reading.status()).isEqualTo(InverterStatus.UNKNOWN);
        assertThat(reading.acPowerW()).isNull();
        assertThat(reading.dailyEnergyKwh()).isNull();
        assertThat(reading.totalEnergyKwh()).isNull();
        assertThat(reading.batterySocPct()).isNull();
        assertThat(reading.importPowerW()).isNull();
        assertThat(reading.exportPowerW()).isNull();
    }

    @Test
    void faultAndStandbyStatusesMapped() {
        assertThat(DeyeRegisters.decode(Map.of(DeyeRegisters.REG_RUNNING_STATUS, 4), now).status())
                .isEqualTo(InverterStatus.FAULT);
        assertThat(DeyeRegisters.decode(Map.of(DeyeRegisters.REG_RUNNING_STATUS, 0), now).status())
                .isEqualTo(InverterStatus.STANDBY);
    }
}
