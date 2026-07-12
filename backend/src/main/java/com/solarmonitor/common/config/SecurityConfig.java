package com.solarmonitor.common.config;

import com.solarmonitor.security.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Segurança stateless com JWT (Etapa 6).
 *
 * <p>Públicos: autenticação, ping, health, OpenAPI/Swagger e o handshake do
 * WebSocket (que valida token próprio no interceptor — ver WebSocketConfig).
 * Escrita administrativa (configurações e usuários) exige ADMIN; o restante
 * da API, qualquer usuário autenticado.</p>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // logout é público de propósito: revogar o refresh token
                        // precisa funcionar mesmo com o access token já expirado
                        // (a posse do refresh token é a autorização).
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/ping", "/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/settings/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        // Refresh manual chama API externa — só ADMIN (cota Open-Meteo).
                        .requestMatchers(HttpMethod.POST, "/api/weather/refresh").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Sem isto, anônimo receberia 403 (Http403ForbiddenEntryPoint
                // default) — o correto para "não autenticado" é 401.
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("""
                            {"type":"about:blank","title":"Não autorizado","status":401,\
                            "detail":"Autentique-se em /api/auth/login"}""");
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
