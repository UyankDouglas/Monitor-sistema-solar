package com.solarmonitor.provider.sim;

import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.provider.EnergyReading;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedProviderTest {

    private final SimulatedProvider provider = new SimulatedProvider();
    private final Inverter inverter = Inverter.builder()
            .name("Simulado")
            .serialNumber("SIM-1")
            .ratedPowerW(10_000)
            .build();

    @Test
    void producesPhysicallyPlausibleReading() {
        EnergyReading reading = provider.read(inverter);

        assertThat(reading.sampledAt()).isNotNull();
        assertThat(reading.status()).isEqualTo(InverterStatus.ONLINE);
        assertThat(reading.acPowerW()).isBetween(0, 10_000);
        assertThat(reading.loadPowerW()).isBetween(0, 3_000);
        assertThat(reading.batterySocPct())
                .isGreaterThanOrEqualTo(BigDecimal.TEN)
                .isLessThanOrEqualTo(new BigDecimal("100"));
        assertThat(reading.dailyEnergyKwh().signum()).isGreaterThanOrEqualTo(0);
        assertThat(reading.gridFrequencyHz().doubleValue()).isBetween(59.9, 60.1);
        assertThat(reading.mpptReadings()).hasSize(2);
        // Balanço: PV = strings somadas (o split não perde potência)
        int mpptSum = reading.mpptReadings().stream()
                .mapToInt(EnergyReading.MpptStringReading::powerW).sum();
        assertThat(mpptSum).isEqualTo(reading.acPowerW());
    }

    @Test
    void energyBalanceHolds() {
        EnergyReading r = provider.read(inverter);

        // PV + bateria(descarga) + importação = carga + exportação + bateria(carga)
        int generation = r.acPowerW()
                + Math.max(r.batteryPowerW(), 0)
                + r.importPowerW();
        int consumption = r.loadPowerW()
                + r.exportPowerW()
                + Math.max(-r.batteryPowerW(), 0);
        assertThat(generation).isEqualTo(consumption);
    }
}
