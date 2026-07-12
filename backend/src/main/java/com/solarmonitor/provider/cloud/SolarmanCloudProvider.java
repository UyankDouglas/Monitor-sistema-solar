package com.solarmonitor.provider.cloud;

import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.InverterStatus;
import com.solarmonitor.provider.EnergyProvider;
import com.solarmonitor.provider.EnergyReading;
import com.solarmonitor.provider.ProviderException;
import com.solarmonitor.provider.ProviderMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coleta via Solarman OpenAPI usando o serial do inversor cadastrado.
 *
 * <p>As chaves do {@code dataList} variam por modelo/firmware; o mapeamento
 * abaixo cobre as chaves usuais da família Deye SUN-xK-SG04LP3 e ignora com
 * segurança o que não reconhece. Na primeira leitura o log INFO lista as
 * chaves recebidas — insumo para calibrar o mapa com o equipamento real.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SolarmanCloudProvider implements EnergyProvider {

    private final SolarmanCloudClient client;

    private volatile boolean keysLogged = false;

    @Override
    public ProviderMode mode() {
        return ProviderMode.CLOUD;
    }

    @Override
    public EnergyReading read(Inverter inverter) throws ProviderException {
        String sn = inverter.getSerialNumber();
        if (sn == null || sn.isBlank() || "CONFIGURAR-SN".equals(sn)) {
            throw new ProviderException("Serial do inversor não configurado — informe o serial real "
                    + "(etiqueta do equipamento) na tela de Configurações");
        }
        List<SolarmanCloudClient.DataPoint> dataList = client.currentData(sn);
        if (dataList.isEmpty()) {
            throw new ProviderException("Solarman devolveu dataList vazio para o serial " + sn);
        }
        Map<String, String> byKey = new HashMap<>();
        for (SolarmanCloudClient.DataPoint dp : dataList) {
            if (dp.key() != null && dp.value() != null) {
                byKey.put(dp.key(), dp.value());
            }
        }
        if (!keysLogged) {
            keysLogged = true;
            log.info("Solarman currentData: {} chaves disponíveis para calibração: {}",
                    byKey.size(), byKey.keySet());
        }

        List<EnergyReading.MpptStringReading> mppt = new ArrayList<>(2);
        mppt.add(new EnergyReading.MpptStringReading((short) 1,
                decimal(byKey, 1, "DV1"), decimal(byKey, 2, "DC1"), intVal(byKey, "DP1")));
        mppt.add(new EnergyReading.MpptStringReading((short) 2,
                decimal(byKey, 1, "DV2"), decimal(byKey, 2, "DC2"), intVal(byKey, "DP2")));

        Integer gridPower = intVal(byKey, "PG_Pt1", "GRID_P_T1");

        return EnergyReading.builder()
                .sampledAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .status(mapStatus(byKey.get("INV_ST1")))
                .acPowerW(intVal(byKey, "APo_t1", "P_T"))
                .loadPowerW(intVal(byKey, "E_Puse_t1", "LOAD_P_T1"))
                .importPowerW(gridPower == null ? null : Math.max(gridPower, 0))
                .exportPowerW(gridPower == null ? null : Math.max(-gridPower, 0))
                .batteryPowerW(intVal(byKey, "B_P1", "BATT_P1"))
                .dailyEnergyKwh(decimal(byKey, 3, "Etdy_ge1", "E_Today"))
                .monthlyEnergyKwh(decimal(byKey, 3, "Etmo_ge1"))
                .totalEnergyKwh(decimal(byKey, 3, "Et_ge0", "E_Total"))
                .gridVoltageL1(decimal(byKey, 1, "AV1"))
                .gridVoltageL2(decimal(byKey, 1, "AV2"))
                .gridVoltageL3(decimal(byKey, 1, "AV3"))
                .gridFrequencyHz(decimal(byKey, 2, "A_Fo1"))
                .batteryVoltage(decimal(byKey, 1, "B_V1"))
                .batteryCurrentA(decimal(byKey, 2, "B_C1"))
                .batterySocPct(decimal(byKey, 2, "B_left_cap1"))
                .batteryTemperatureC(decimal(byKey, 1, "B_T1"))
                .inverterTemperatureC(decimal(byKey, 1, "T_AC_RDT1", "AC_RDT_T1"))
                .mpptReadings(mppt)
                .build();
    }

    private InverterStatus mapStatus(String raw) {
        if (raw == null) {
            return InverterStatus.UNKNOWN;
        }
        // A OpenAPI reporta status textual ("1"=online) ou descritivo conforme o modelo.
        return switch (raw.trim().toLowerCase()) {
            case "1", "normal", "online", "on-grid" -> InverterStatus.ONLINE;
            case "2", "alarm", "fault" -> InverterStatus.FAULT;
            case "0", "standby", "waiting" -> InverterStatus.STANDBY;
            case "offline" -> InverterStatus.OFFLINE;
            default -> InverterStatus.UNKNOWN;
        };
    }

    /** Primeiro valor numérico presente entre as chaves candidatas, arredondado a inteiro. */
    private Integer intVal(Map<String, String> data, String... keys) {
        BigDecimal value = decimal(data, 0, keys);
        return value == null ? null : value.intValue();
    }

    /** Primeiro valor numérico presente entre as chaves candidatas, com {@code scale} casas. */
    private BigDecimal decimal(Map<String, String> data, int scale, String... keys) {
        for (String key : keys) {
            String raw = data.get(key);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return new BigDecimal(raw.trim()).setScale(scale, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                log.debug("Valor não numérico para chave Solarman '{}': '{}'", key, raw);
            }
        }
        return null;
    }
}
