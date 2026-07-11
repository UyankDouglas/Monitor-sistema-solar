package com.solarmonitor.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança PROVISÓRIA para o bootstrap.
 *
 * <p>Neste momento todos os endpoints estão liberados apenas para permitir
 * validar a stack (health, Swagger, ping). A Etapa 6 substitui esta classe
 * por autenticação stateless com JWT + refresh token e controle de papéis
 * (ADMIN / USER).</p>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
