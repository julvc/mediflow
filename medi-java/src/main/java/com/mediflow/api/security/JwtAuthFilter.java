package com.mediflow.api.security;

import com.mediflow.api.domain.Rol;
import com.mediflow.api.exception.JwtInvalidoException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validarYObtenerClaims(token);
                UsuarioPrincipal principal = principalDesdeClaims(claims);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ex) {
                // Token invalido/expirado o claims malformados: se deja la request sin
                // autenticar. El AuthenticationEntryPoint (SecurityConfig) decide el 401
                // mas abajo si el endpoint lo requiere — no se corta la cadena aca para
                // no romper rutas publicas si llega un header Authorization basura.
                // Cubre: JwtInvalidoException, IllegalArgumentException (rol invalido),
                // NullPointerException (claim faltante).
                request.setAttribute("jwtError", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private UsuarioPrincipal principalDesdeClaims(Claims claims) {
        Long usuarioId = claims.get("usuarioId", Number.class).longValue();
        String email = claims.getSubject();
        Rol rol = Rol.valueOf(claims.get("rol", String.class));
        Long pacienteId = numeroOrNull(claims.get("pacienteId", Number.class));
        Long profesionalId = numeroOrNull(claims.get("profesionalId", Number.class));
        return new UsuarioPrincipal(usuarioId, email, rol, pacienteId, profesionalId);
    }

    private Long numeroOrNull(Number numero) {
        return numero != null ? numero.longValue() : null;
    }
}
