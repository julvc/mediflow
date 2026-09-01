package com.mediflow.api.security;

import com.mediflow.api.domain.Rol;
import com.mediflow.api.exception.JwtInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "secreto-de-test-minimo-32-caracteres-para-hmac-sha256";

    private final JwtService jwtService = new JwtService(SECRET, 15);

    @Test
    void generarAccessToken_devuelveTokenConClaimsCorrectos() {
        String token = jwtService.generarAccessToken(1L, "ana@test.cl", Rol.PACIENTE, 10L, null);

        Claims claims = jwtService.validarYObtenerClaims(token);

        assertThat(claims.getSubject()).isEqualTo("ana@test.cl");
        assertThat(claims.get("usuarioId", Number.class).longValue()).isEqualTo(1L);
        assertThat(claims.get("rol", String.class)).isEqualTo("PACIENTE");
        assertThat(claims.get("pacienteId", Number.class).longValue()).isEqualTo(10L);
        assertThat(claims.get("profesionalId")).isNull();
    }

    @Test
    void validarYObtenerClaims_conTokenExpirado_lanzaJwtInvalido() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenExpirado = Jwts.builder()
                .subject("ana@test.cl")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(1)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService.validarYObtenerClaims(tokenExpirado))
                .isInstanceOf(JwtInvalidoException.class);
    }

    @Test
    void validarYObtenerClaims_conTokenMalformado_lanzaJwtInvalido() {
        assertThatThrownBy(() -> jwtService.validarYObtenerClaims("esto-no-es-un-jwt"))
                .isInstanceOf(JwtInvalidoException.class);
    }

    @Test
    void validarYObtenerClaims_conFirmaDistinta_lanzaJwtInvalido() {
        SecretKey otraKey = Keys.hmacShaKeyFor("otro-secreto-completamente-distinto-32-caracteres".getBytes(StandardCharsets.UTF_8));
        String tokenConOtraFirma = Jwts.builder()
                .subject("ana@test.cl")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otraKey)
                .compact();

        assertThatThrownBy(() -> jwtService.validarYObtenerClaims(tokenConOtraFirma))
                .isInstanceOf(JwtInvalidoException.class);
    }
}
