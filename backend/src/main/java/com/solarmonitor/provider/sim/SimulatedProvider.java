package com.solarmonitor.provider.sim;

import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.provider.EnergyProvider;
import com.solarmonitor.provider.EnergyReading;
import com.solarmonitor.provider.ProviderMode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provider simulado: gera uma curva solar realista em função da hora local —
 * permite desenvolver e demonstrar dashboard, histórico e alertas sem
 * hardware nem credenciais. Ativado pelo {@code provider.mode=SIMULATED}
 * (padrão de fábrica desde a migration V9).
 *
 * <p>Modelo: geração senoidal entre 06h e 18h com pico ao meio-dia e ruído de
 * nuvem; a casa consome carga base + variação; bateria absorve/entrega a
 * diferença dentro dos limites de SOC; o excedente vai para a rede.</p>
 */
@Component
public class SimulatedProvider implements EnergyProvider {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int SUNRISE_HOUR = 6;
    private static final int SUNSET_HOUR = 18;

    @Override
    public ProviderMode mode() {
        return ProviderMode.SIMULATED;
    }

    @Override
    public EnergyReading read(Inverter inverter) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ZonedDateTime local = now.atZone(ZONE);

        int ratedPowerW = inverter.getRatedPowerW() == null ? 10_000 : inverter.getRatedPowerW();
        double solarFactor = solarFactor(local, random);
        int pvPower = (int) Math.round(ratedPowerW * solarFactor);

        // Split PV entre as duas strings com leve assimetria (orientações distintas).
        int pv1 = (int) Math.round(pvPower * random.nextDouble(0.48, 0.52));
        int pv2 = pvPower - pv1;

        int loadPower = 400 + random.nextInt(0, 1_800);

        // SOC oscila ao longo do dia: carrega com sol, descarrega à noite.
        double socBase = 45 + 40 * Math.sin(Math.PI * (hourOfDay(local) - 8) / 14.0);
        BigDecimal soc = clamp(socBase + random.nextDouble(-2, 2), 10, 100, 2);

        int surplus = pvPower - loadPower;
        int batteryPower;   // positivo = descarregando
        int gridExport = 0;
        int gridImport = 0;
        if (surplus >= 0) {
            int charging = Math.min(surplus, soc.doubleValue() >= 99 ? 0 : 3_000);
            batteryPower = -charging;
            gridExport = surplus - charging;
        } else {
            int discharging = Math.min(-surplus, soc.doubleValue() <= 12 ? 0 : 5_000);
            batteryPower = discharging;
            gridImport = -surplus - discharging;
        }

        double dayProgress = solarDayProgress(local);
        BigDecimal dailyEnergy = BigDecimal.valueOf(ratedPowerW / 1000.0 * 5.2 * dayProgress)
                .setScale(3, RoundingMode.HALF_UP);

        return EnergyReading.builder()
                .sampledAt(now)
                .status(InverterStatus.ONLINE)
                .acPowerW(pvPower)
                .loadPowerW(loadPower)
                .exportPowerW(gridExport)
                .importPowerW(gridImport)
                .batteryPowerW(batteryPower)
                .dailyEnergyKwh(dailyEnergy)
                .totalEnergyKwh(new BigDecimal("15230.500").add(dailyEnergy))
                .gridVoltageL1(noisy(random, 220, 3, 1))
                .gridVoltageL2(noisy(random, 220, 3, 1))
                .gridVoltageL3(noisy(random, 221, 3, 1))
                .gridFrequencyHz(noisy(random, 60, 0.05, 2))
                .batteryVoltage(noisy(random, 52.5, 0.8, 1))
                .batteryCurrentA(BigDecimal.valueOf(Math.abs(batteryPower) / 52.5)
                        .setScale(2, RoundingMode.HALF_UP))
                .batterySocPct(soc)
                .batteryTemperatureC(noisy(random, 30, 2, 1))
                .inverterTemperatureC(noisy(random, 42 + 12 * solarFactor, 1.5, 1))
                .mpptReadings(List.of(
                        mpptString(random, (short) 1, pv1),
                        mpptString(random, (short) 2, pv2)))
                .build();
    }

    /** Fator de geração 0..~0.85 com forma senoidal e ruído de nebulosidade. */
    private double solarFactor(ZonedDateTime local, ThreadLocalRandom random) {
        double hour = hourOfDay(local);
        if (hour < SUNRISE_HOUR || hour > SUNSET_HOUR) {
            return 0;
        }
        double curve = Math.sin(Math.PI * (hour - SUNRISE_HOUR) / (SUNSET_HOUR - SUNRISE_HOUR));
        double clouds = random.nextDouble(0.75, 1.0);
        return Math.max(0, curve * clouds * 0.85);
    }

    /** Fração do "dia solar" já decorrida (0 antes das 6h, 1 após as 18h). */
    private double solarDayProgress(ZonedDateTime local) {
        double hour = hourOfDay(local);
        return Math.clamp((hour - SUNRISE_HOUR) / (double) (SUNSET_HOUR - SUNRISE_HOUR), 0.0, 1.0);
    }

    private double hourOfDay(ZonedDateTime local) {
        return local.getHour() + local.getMinute() / 60.0;
    }

    private EnergyReading.MpptStringReading mpptString(ThreadLocalRandom random, short index, int powerW) {
        BigDecimal voltage = powerW == 0
                ? noisy(random, 45, 8, 1)                       // tensão de circuito aberto baixa à noite
                : noisy(random, 380, 15, 1);
        BigDecimal current = voltage.signum() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(powerW / voltage.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        return new EnergyReading.MpptStringReading(index, voltage, current, powerW);
    }

    private BigDecimal noisy(ThreadLocalRandom random, double base, double spread, int scale) {
        return BigDecimal.valueOf(base + random.nextDouble(-spread, spread))
                .setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(double value, double min, double max, int scale) {
        return BigDecimal.valueOf(Math.clamp(value, min, max)).setScale(scale, RoundingMode.HALF_UP);
    }
}
