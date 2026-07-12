package com.solarmonitor.provider.cloud;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.solarmonitor.config.service.ConfigurationService;
import com.solarmonitor.provider.ProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP da Solarman OpenAPI (plataforma que atende o app Solarman
 * Smart). Autentica via appId/appSecret + e-mail/senha(SHA-256) e mantém o
 * bearer token em cache até perto da expiração.
 *
 * <p>Credenciais vêm da tabela {@code configurations}
 * ({@code provider.cloud.*}); a URL base fica em
 * {@code app.solarman.cloud.base-url} (application.yml) por não ser segredo.
 * O appId/appSecret são obtidos pelo usuário no portal
 * <a href="https://home.solarmanpv.com">home.solarmanpv.com</a> (Account →
 * API Service).</p>
 */
@Component
@Slf4j
public class SolarmanCloudClient {

    private final ConfigurationService configurations;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public SolarmanCloudClient(ConfigurationService configurations,
                               RestClient.Builder builder,
                               @Value("${app.solarman.cloud.base-url}") String baseUrl) {
        this.configurations = configurations;
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Dados instantâneos do dispositivo: lista chave→valor conforme publicada
     * pela Solarman (chaves variam por modelo/firmware).
     */
    public List<DataPoint> currentData(String deviceSn) throws ProviderException {
        try {
            CurrentDataResponse response = restClient.post()
                    .uri("/device/v1.0/currentData?language=en")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token())
                    .body(Map.of("deviceSn", deviceSn))
                    .retrieve()
                    .body(CurrentDataResponse.class);
            if (response == null || !response.isSuccess()) {
                // A Solarman sinaliza token expirado/revogado como HTTP 200 +
                // success=false. Invalidar aqui garante reautenticação no
                // próximo ciclo; o custo em erro de negócio não-auth é um
                // token extra — irrelevante frente a ficar preso num token
                // morto até o fim do TTL (~60 dias).
                invalidateToken();
                throw new ProviderException("Solarman currentData sem sucesso: "
                        + (response == null ? "resposta vazia" : response.msg()));
            }
            return response.dataList() == null ? List.of() : response.dataList();
        } catch (RestClientException e) {
            // Só descarta o token quando a credencial foi de fato rejeitada;
            // em timeout/5xx o token segue válido — reusar evita martelar o
            // endpoint de token (o mais rate-limitado da Solarman) a cada 5 s.
            if (e instanceof HttpClientErrorException.Unauthorized
                    || e instanceof HttpClientErrorException.Forbidden) {
                invalidateToken();
            }
            throw new ProviderException("Falha HTTP na Solarman OpenAPI: " + e.getMessage(), e);
        }
    }

    private synchronized String token() throws ProviderException {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        String appId = require("provider.cloud.app-id");
        String appSecret = require("provider.cloud.app-secret");
        String email = require("provider.cloud.email");
        String passwordSha256 = configurations.getString("provider.cloud.password-sha256")
                .orElseGet(() -> sha256(configurations.getString("provider.cloud.password").orElse("")));
        if (passwordSha256.isBlank()) {
            throw new ProviderException("Senha da conta Solarman não configurada "
                    + "(provider.cloud.password-sha256 ou provider.cloud.password)");
        }
        try {
            TokenResponse response = restClient.post()
                    .uri("/account/v1.0/token?appId={appId}&language=en", appId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("appSecret", appSecret,
                            "email", email,
                            "password", passwordSha256))
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new ProviderException("Solarman token não emitido: "
                        + (response == null ? "resposta vazia" : response.msg()));
            }
            cachedToken = response.accessToken();
            long ttlSeconds = response.expiresIn() == null ? 3600 : Long.parseLong(response.expiresIn());
            // Renova 5 min antes de expirar para nunca usar token na iminência de morrer.
            tokenExpiresAt = Instant.now().plusSeconds(Math.max(ttlSeconds - 300, 60));
            log.info("Token Solarman renovado; expira em ~{} s", ttlSeconds);
            return cachedToken;
        } catch (RestClientException e) {
            throw new ProviderException("Falha ao autenticar na Solarman OpenAPI: " + e.getMessage(), e);
        }
    }

    private void invalidateToken() {
        cachedToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    private String require(String key) throws ProviderException {
        return configurations.getString(key).orElseThrow(() -> new ProviderException(
                key + " não configurado — preencha as credenciais da API Solarman na tela de Configurações"));
    }

    static String sha256(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM sem SHA-256", e);
        }
    }

    // --- DTOs da OpenAPI --------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") String expiresIn,
                         Boolean success,
                         String msg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CurrentDataResponse(Boolean success,
                               String msg,
                               String deviceSn,
                               List<DataPoint> dataList) {
        boolean isSuccess() {
            return Boolean.TRUE.equals(success);
        }
    }

    /** Um ponto de dado da Solarman: chave técnica, valor textual e unidade. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataPoint(String key, String value, String unit, String name) {
    }
}
