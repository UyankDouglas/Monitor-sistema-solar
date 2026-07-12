package com.solarmonitor.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida o bearer token e popula o SecurityContext.
 *
 * <p>Aplica também a regra de troca de senha pendente: com a claim
 * {@code mcp=true}, apenas {@code /api/auth/**} é permitido — o usuário não
 * navega no sistema com a senha de fábrica.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        Claims claims = jwtService.parse(header.substring(7)).orElse(null);
        if (claims == null) {
            chain.doFilter(request, response); // token inválido → segue anônimo → 401 adiante
            return;
        }

        boolean mustChangePassword = Boolean.TRUE.equals(
                claims.get(JwtService.CLAIM_MUST_CHANGE_PASSWORD, Boolean.class));
        String path = request.getRequestURI();
        if (mustChangePassword && path.startsWith("/api/") && !path.startsWith("/api/auth/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Troca de senha obrigatória","status":403,\
                    "detail":"Troque a senha inicial em /api/auth/change-password antes de usar o sistema"}""");
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(JwtService.CLAIM_ROLES, List.class);
        var authorities = (roles == null ? List.<String>of() : roles).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }
}
