package com.solarmonitor.config.web;

import com.solarmonitor.config.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Configurações",
        description = "Configurações globais em runtime — intervalo, provider, tarifas, credenciais")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @Operation(summary = "Lista as configurações globais",
            description = "Valores de chaves sensíveis (secret/password) voltam mascarados.")
    public List<ConfigurationDto> list() {
        return settingsService.list();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Atualiza uma configuração",
            description = "Só chaves existentes; valor validado por tipo e regra de negócio. "
                    + "Efeito no ciclo seguinte do scheduler, sem restart.")
    public ConfigurationDto update(@PathVariable String key,
                                   @RequestBody @Valid UpdateSettingRequest request) {
        return settingsService.update(key, request.value());
    }

    /** Corpo do PUT: apenas o novo valor. */
    public record UpdateSettingRequest(
            @NotNull(message = "value é obrigatório (use string vazia para limpar)")
            @Size(max = 500, message = "value excede 500 caracteres")
            String value) {
    }
}
