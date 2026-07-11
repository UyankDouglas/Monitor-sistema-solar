package com.solarmonitor.plant.repository;

import com.solarmonitor.AbstractRepositoryTest;
import com.solarmonitor.config.domain.ConfigScope;
import com.solarmonitor.config.domain.Configuration;
import com.solarmonitor.config.repository.ConfigurationRepository;
import com.solarmonitor.plant.domain.Device;
import com.solarmonitor.plant.domain.DeviceType;
import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.domain.ProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AssetRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private InverterRepository inverterRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void defaultPlantAndInverterSeededByMigration() {
        assertThat(plantRepository.findAll())
                .anySatisfy(plant -> {
                    assertThat(plant.getName()).isEqualTo("Minha Usina");
                    assertThat(plant.getTimezone()).isEqualTo("America/Sao_Paulo");
                    assertThat(plant.getKwhPrice()).isEqualByComparingTo("0.95");
                });

        Optional<Inverter> inverter = inverterRepository.findBySerialNumber("CONFIGURAR-SN");
        assertThat(inverter).isPresent();
        assertThat(inverter.get().getModel()).isEqualTo("SUN-10K-SG04LP3");
        assertThat(inverter.get().getRatedPowerW()).isEqualTo(10_000);
        assertThat(inverter.get().getMpptCount()).isEqualTo((short) 2);
        assertThat(inverter.get().getProviderType()).isEqualTo(ProviderType.CLOUD);
    }

    @Test
    void devicePersistsJsonbMetadata() {
        Inverter inverter = inverterRepository.findBySerialNumber("CONFIGURAR-SN").orElseThrow();

        Device logger = Device.builder()
                .inverter(inverter)
                .type(DeviceType.LOGGER)
                .serialNumber("SN-LOGGER-1")
                .ipAddress("192.168.1.50")
                .port(8899)
                .metadata(Map.of("firmware", "MW3_16U_5406_1.57", "wifi_rssi", -61))
                .build();
        entityManager.persistAndFlush(logger);
        entityManager.clear();

        Device reloaded = deviceRepository.findAllByInverter_IdAndType(inverter.getId(), DeviceType.LOGGER).get(0);
        assertThat(reloaded.getMetadata())
                .containsEntry("firmware", "MW3_16U_5406_1.57")
                .containsEntry("wifi_rssi", -61);
    }

    @Test
    void globalConfigurationsSeededByMigration() {
        Optional<Configuration> interval =
                configurationRepository.findByScopeAndKey(ConfigScope.GLOBAL, "scheduler.reading-interval-ms");

        assertThat(interval).isPresent();
        assertThat(interval.get().getValue()).isEqualTo("5000");

        assertThat(configurationRepository.findAllByScope(ConfigScope.GLOBAL))
                .extracting(Configuration::getKey)
                .contains("energy.kwh-price", "energy.currency", "app.timezone",
                        "provider.mode", "provider.local.logger-port");
    }
}
