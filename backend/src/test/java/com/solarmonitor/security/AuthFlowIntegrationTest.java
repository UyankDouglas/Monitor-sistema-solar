package com.solarmonitor.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo de autenticação num único cenário sequencial (métodos
 * separados criariam dependência de ordem — a senha muda no meio):
 * login de fábrica → bloqueio por troca pendente → troca → acesso → rotação
 * de refresh → detecção de reuso → RBAC de usuário comum.
 */
@Testcontainers
@SpringBootTest(properties = "app.scheduler.enabled=false")
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:2.17.2-pg16")
                    .asCompatibleSubstituteFor("postgres"));

    private static final String NEW_ADMIN_PASSWORD = "NovaSenhaSegura#2026";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestsGet401() throws Exception {
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
        // Públicos continuam abertos
        mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void wrongCredentialsGet401WithoutDetail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"senha-errada"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autorizado"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nao-existe","password":"qualquer"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullAuthenticationLifecycle() throws Exception {
        // 1. Login de fábrica: tokens emitidos, troca de senha pendente
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.roles").isArray())
                .andReturn();
        String factoryAccess = read(login, "$.accessToken");
        String factoryRefresh = read(login, "$.refreshToken");

        // 2. Com troca pendente: API bloqueada (403), auth liberado
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + factoryAccess))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Troca de senha obrigatória"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + factoryAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));

        // 3. Troca de senha: atual errada → 401; curta demais → 400; correta → 200
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + factoryAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"errada","newPassword":"%s"}""".formatted(NEW_ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + factoryAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"admin123","newPassword":"curta"}"""))
                .andExpect(status().isBadRequest());
        MvcResult changed = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + factoryAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"admin123","newPassword":"%s"}""".formatted(NEW_ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();
        String access = read(changed, "$.accessToken");
        String refreshA = read(changed, "$.refreshToken");
        // (a checagem do refresh de fábrica revogado fica para o FINAL:
        //  apresentar token revogado dispara a detecção de reuso e revogaria
        //  TODAS as sessões — inclusive o refreshA dos próximos passos)

        // 4. Token novo navega normalmente
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // 5. Rotação: A → B; reuso de A → 401 e TODAS as sessões caem
        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshA)))
                .andExpect(status().isOk())
                .andReturn();
        String refreshB = read(rotated, "$.refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("reutilizado")));
        // B foi revogado pela detecção de reuso
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshB)))
                .andExpect(status().isUnauthorized());

        // 6. RBAC: admin cria usuário comum; USER não altera settings nem vê /api/users
        MvcResult relogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"%s"}""".formatted(NEW_ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String adminAccess = read(relogin, "$.accessToken");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maria","email":"maria@casa.com","fullName":"Maria",
                                 "temporaryPassword":"senha-temporaria-1","roles":["USER"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        MvcResult mariaLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"maria","password":"senha-temporaria-1"}"""))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult mariaChanged = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + read(mariaLogin, "$.accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"senha-temporaria-1","newPassword":"senha-da-maria-8"}"""))
                .andExpect(status().isOk())
                .andReturn();
        String mariaAccess = read(mariaChanged, "$.accessToken");

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + mariaAccess))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/settings/provider.mode")
                        .header("Authorization", "Bearer " + mariaAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"LOCAL"}"""))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + mariaAccess))
                .andExpect(status().isForbidden());

        // 7. Logout revoga o refresh da sessão
        String adminRefresh = read(relogin, "$.refreshToken");
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(adminRefresh)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(adminRefresh)))
                .andExpect(status().isUnauthorized());

        // 8. O refresh de fábrica (revogado na troca de senha lá atrás)
        //    também é recusado — dispara o caminho de reuso, inofensivo aqui.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(factoryRefresh)))
                .andExpect(status().isUnauthorized());
    }

    private String read(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }
}
