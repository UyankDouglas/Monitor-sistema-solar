package com.solarmonitor.provider.cloud;

import com.solarmonitor.config.service.ConfigurationService;
import com.solarmonitor.provider.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SolarmanCloudClientTest {

    private static final String BASE = "https://api.test";

    private ConfigurationService configurations;
    private MockRestServiceServer server;
    private SolarmanCloudClient client;

    @BeforeEach
    void setUp() {
        configurations = mock(ConfigurationService.class);
        when(configurations.getString("provider.cloud.app-id")).thenReturn(Optional.of("APP123"));
        when(configurations.getString("provider.cloud.app-secret")).thenReturn(Optional.of("SECRET"));
        when(configurations.getString("provider.cloud.email")).thenReturn(Optional.of("user@test.com"));
        when(configurations.getString("provider.cloud.password-sha256"))
                .thenReturn(Optional.of("ab12cd34"));

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SolarmanCloudClient(configurations, builder, BASE);
    }

    @Test
    void authenticatesAndFetchesCurrentData() throws Exception {
        server.expect(requestTo(BASE + "/account/v1.0/token?appId=APP123&language=en"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.appSecret").value("SECRET"))
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.password").value("ab12cd34"))
                .andRespond(withSuccess("""
                        {"access_token":"tok-1","expires_in":"5184000","success":true}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/device/v1.0/currentData?language=en"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(jsonPath("$.deviceSn").value("SN-123"))
                .andRespond(withSuccess("""
                        {"success":true,"deviceSn":"SN-123","dataList":[
                          {"key":"APo_t1","value":"5200","unit":"W","name":"Total AC Output Power"},
                          {"key":"B_left_cap1","value":"78.5","unit":"%","name":"SoC"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<SolarmanCloudClient.DataPoint> data = client.currentData("SN-123");

        assertThat(data).hasSize(2);
        assertThat(data.get(0).key()).isEqualTo("APo_t1");
        assertThat(data.get(0).value()).isEqualTo("5200");
        server.verify();
    }

    @Test
    void reusesCachedTokenOnSecondCall() throws Exception {
        server.expect(requestTo(BASE + "/account/v1.0/token?appId=APP123&language=en"))
                .andRespond(withSuccess("""
                        {"access_token":"tok-1","expires_in":"5184000","success":true}
                        """, MediaType.APPLICATION_JSON));
        // Apenas UMA expectativa de token; duas de dados.
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo(BASE + "/device/v1.0/currentData?language=en"))
                    .andExpect(header("Authorization", "Bearer tok-1"))
                    .andRespond(withSuccess("""
                            {"success":true,"dataList":[{"key":"P_T","value":"1"}]}
                            """, MediaType.APPLICATION_JSON));
        }

        client.currentData("SN-123");
        client.currentData("SN-123");

        server.verify();
    }

    @Test
    void reauthenticatesAfterAuthErrorSignaledWithHttp200() throws Exception {
        // 1º token
        server.expect(requestTo(BASE + "/account/v1.0/token?appId=APP123&language=en"))
                .andRespond(withSuccess("""
                        {"access_token":"tok-morto","expires_in":"5184000","success":true}
                        """, MediaType.APPLICATION_JSON));
        // Solarman sinaliza token inválido como HTTP 200 + success=false
        server.expect(requestTo(BASE + "/device/v1.0/currentData?language=en"))
                .andRespond(withSuccess("""
                        {"success":false,"msg":"auth invalid token"}
                        """, MediaType.APPLICATION_JSON));
        // Próxima chamada DEVE reautenticar (novo token) em vez de reusar o cache
        server.expect(requestTo(BASE + "/account/v1.0/token?appId=APP123&language=en"))
                .andRespond(withSuccess("""
                        {"access_token":"tok-novo","expires_in":"5184000","success":true}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/device/v1.0/currentData?language=en"))
                .andExpect(header("Authorization", "Bearer tok-novo"))
                .andRespond(withSuccess("""
                        {"success":true,"dataList":[{"key":"P_T","value":"1"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.currentData("SN-123"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("auth invalid token");
        assertThat(client.currentData("SN-123")).hasSize(1);

        server.verify();
    }

    @Test
    void failsClearlyWhenCredentialsMissing() {
        when(configurations.getString("provider.cloud.app-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.currentData("SN-123"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("provider.cloud.app-id");
    }

    @Test
    void sha256IsLowercaseHex() {
        assertThat(SolarmanCloudClient.sha256("senha123"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }
}
