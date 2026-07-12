package com.solarmonitor.config.service;

import com.solarmonitor.AbstractRepositoryTest;
import com.solarmonitor.config.repository.ConfigurationRepository;
import com.solarmonitor.config.web.ConfigurationDto;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras do serviço de configurações sobre os seeds reais (V8/V9).
 * Instanciado à mão sobre o repositório do slice @DataJpaTest — a transação
 * do teste faz o rollback.
 */
class SettingsServiceTest extends AbstractRepositoryTest {

    @Autowired
    private ConfigurationRepository repository;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(repository);
    }

    @Test
    void listsGlobalsWithSecretsMasked() {
        service.update("provider.cloud.app-secret", "super-segredo-123");

        List<ConfigurationDto> settings = service.list();

        assertThat(settings).extracting(ConfigurationDto::key)
                .contains("scheduler.reading-interval-ms", "provider.mode", "energy.kwh-price");
        ConfigurationDto secret = settings.stream()
                .filter(s -> s.key().equals("provider.cloud.app-secret")).findFirst().orElseThrow();
        assertThat(secret.secret()).isTrue();
        assertThat(secret.value()).isEqualTo(SettingsService.MASK);
        assertThat(secret.value()).doesNotContain("super-segredo");

        ConfigurationDto mode = settings.stream()
                .filter(s -> s.key().equals("provider.mode")).findFirst().orElseThrow();
        assertThat(mode.secret()).isFalse();
        assertThat(mode.value()).isEqualTo("SIMULATED"); // V9 muda o default de fábrica
    }

    @Test
    void updateValidatesTypeAndBusinessRules() {
        assertThat(service.update("scheduler.reading-interval-ms", "10000").value()).isEqualTo("10000");

        assertThatThrownBy(() -> service.update("scheduler.reading-interval-ms", "500"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1000");
        assertThatThrownBy(() -> service.update("scheduler.reading-interval-ms", "abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inteiro");
        assertThatThrownBy(() -> service.update("provider.mode", "MODO_INEXISTENTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOUD, LOCAL, SIMULATED");
        assertThatThrownBy(() -> service.update("energy.kwh-price", "-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> service.update("chave.que.nao.existe", "x"))
                .isInstanceOf(EntityNotFoundException.class);

        // Troca de modo válida (a tela de Configurações fará exatamente isso)
        assertThat(service.update("provider.mode", "local").value()).isEqualTo("local");
    }
}
