package com.mediflow.api.security;

import com.mediflow.api.domain.Rol;
import com.mediflow.api.exception.JwtInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

// No conoce refresh tokens (esos viven en BD, ver RefreshTokenRepository) — este
// servicio solo firma/verifica el access token JWT stateless de cada request.
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenMinutos;

    public JwtService(
            @Value("${mediflow.jwt.secret}") String secret,
            @Value("${mediflow.jwt.access-token-minutos}") long accessTokenMinutos) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutos = accessTokenMinutos;
    }

    public String generarAccessToken(Long usuarioId, String email, Rol rol, Long pacienteId, Long profesionalId) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("usuarioId", usuarioId)
                .claim("rol", rol.name())
                .claim("pacienteId", pacienteId)
                .claim("profesionalId", profesionalId)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(accessTokenMinutos * 60)))
                .signWith(key)
                .compact();
    }

    public Claims validarYObtenerClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            throw new JwtInvalidoException("Token invalido o expirado");
        }
    }
}
