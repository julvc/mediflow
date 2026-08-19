# Autenticación, roles y tokens JWT en medi-java — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar autenticación (login), autorización por rol (RBAC: PACIENTE/PROFESIONAL/ADMIN) y tokens JWT (access + refresh) a `medi-java`, dejando un contrato de API estable para que el frontend construya login/perfil sobre él.

**Architecture:** Filtro JWT manual (`OncePerRequestFilter` + `jjwt`) que valida el `accessToken` en cada request y puebla el `SecurityContext` sin tocar la base de datos (claims stateless). Una entidad `Usuario` separada del dominio (vinculada opcionalmente a `Paciente`/`Profesional`) maneja login/roles; los refresh tokens viven en BD como hash SHA-256, con rotación en cada uso.

**Tech Stack:** Spring Boot 3.3.4, Spring Security 6.x (`spring-boot-starter-security`), `io.jsonwebtoken:jjwt` 0.13.0, BCrypt, Postgres `pgcrypto` (solo para el seed de datos).

**Spec:** `docs/superpowers/specs/2026-08-19-auth-jwt-roles-design.md`

## Global Constraints

- Todo bajo `/api/v1/auth/**` es público (sin JWT); todo lo demás exige un `accessToken` válido (spec, sección "Endpoints nuevos" y "Autorización por rol").
- Roles: `PACIENTE`, `PROFESIONAL`, `ADMIN` — sin roles genéricos (spec, decisión de roles).
- `Usuario` es una entidad separada del dominio, vinculada opcionalmente 1 a 1 a `Paciente` o `Profesional` (spec, "Modelo de datos").
- Refresh token: se persiste el hash SHA-256 (hex, 64 caracteres), nunca el valor en texto plano (spec, corrección de ambigüedad ya aplicada).
- Access token expira en 15 min, refresh token en 7 días (spec, "Estrategia JWT" acordada en brainstorming).
- Reusar `GlobalExceptionHandler`, `ErrorResponse`, `ResourceNotFoundException`, `ConflictoDeNegocioException` ya existentes — no crear un manejador de errores paralelo (spec, "Manejo de errores").

**Desviación deliberada del spec (descubierta al bajar a nivel de código, no estaba prevista al escribir el spec):** el spec no definía cómo se crea el primer `ADMIN` ni impedía que cualquier anónimo se auto-registrara como `ADMIN` llamando `POST /api/v1/auth/registro` con `rol=ADMIN` (ruta pública). Eso es una escalación de privilegios real, no un detalle menor — se corrige en dos partes: (1) `AuthServiceImpl.registrar` solo permite auto-registro público para `rol=PACIENTE`; registrar `PROFESIONAL`/`ADMIN` exige que quien llama ya esté autenticado como `ADMIN`. (2) La migración `V2` siembra un único usuario `ADMIN` de arranque (`admin@mediflow.cl` / `admin1234`, hash generado con `pgcrypto` en la propia migración, no un hash pegado a mano) para poder operar el sistema antes de que exista cualquier otro admin. También se extiende `@PreAuthorize("hasRole('ADMIN')")` al `DELETE` de `DocumentoController`, que el spec no mencionó explícitamente pero que por consistencia con `Paciente`/`Profesional` debía quedar igual de protegido (dejarlo abierto habría sido el único `DELETE` sin restricción).

---

### Task 1: Dependencias, configuración y modelo de datos

**Files:**
- Modify: `medi-java/pom.xml`
- Modify: `medi-java/src/main/resources/application.yml`
- Create: `medi-java/src/main/resources/db/migration/V2__auth_schema.sql`
- Create: `medi-java/src/main/java/com/mediflow/api/domain/Rol.java`
- Create: `medi-java/src/main/java/com/mediflow/api/domain/Usuario.java`
- Create: `medi-java/src/main/java/com/mediflow/api/domain/RefreshToken.java`
- Create: `medi-java/src/main/java/com/mediflow/api/repository/UsuarioRepository.java`
- Create: `medi-java/src/main/java/com/mediflow/api/repository/RefreshTokenRepository.java`

**Interfaces:**
- Produces: `Rol` (enum `PACIENTE`/`PROFESIONAL`/`ADMIN`), `Usuario` (getters: `getId()`, `getEmail()`, `getPasswordHash()`, `getRol()`, `getPacienteId()`, `getProfesionalId()`, `isActivo()`), `RefreshToken` (getters: `getId()`, `getUsuarioId()`, `getTokenHash()`, `getExpiraEn()`, `isRevocado()`, setter `setRevocado(boolean)`), `UsuarioRepository.findByEmail(String): Optional<Usuario>`, `UsuarioRepository.existsByEmail(String): boolean`, `RefreshTokenRepository.findByTokenHashAndRevocadoFalse(String): Optional<RefreshToken>`. Estos nombres los consume el Task 6 (`AuthServiceImpl`).

- [ ] **Step 1: Agregar dependencias al pom.xml**

En `medi-java/pom.xml`, agregar dentro de `<dependencies>` (después del bloque "Observabilidad", antes de "Documentación de la API"):

```xml
        <!-- Seguridad: autenticación por JWT -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.13.0</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.13.0</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.13.0</version>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: Agregar configuración de JWT a application.yml**

En `medi-java/src/main/resources/application.yml`, agregar al final del archivo:

```yaml

mediflow:
  jwt:
    secret: ${JWT_SECRET:mediflow-dev-secret-cambiar-en-produccion-32-caracteres-minimo}
    access-token-minutos: 15
    refresh-token-dias: 7
```

- [ ] **Step 3: Crear el enum Rol**

Crear `medi-java/src/main/java/com/mediflow/api/domain/Rol.java`:

```java
package com.mediflow.api.domain;

public enum Rol {
    PACIENTE, PROFESIONAL, ADMIN
}
```

- [ ] **Step 4: Crear la entidad Usuario**

Crear `medi-java/src/main/java/com/mediflow/api/domain/Usuario.java`:

```java
package com.mediflow.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// pacienteId/profesionalId son columnas planas (no @ManyToOne) a propósito: esta
// entidad es identidad/login, no necesita navegar el grafo de Paciente/Profesional
// para autenticar — evita lazy-loading innecesario en el filtro JWT stateless.
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "profesional_id")
    private Long profesionalId;

    @Column(nullable = false)
    private boolean activo;
}
```

- [ ] **Step 5: Crear la entidad RefreshToken**

Crear `medi-java/src/main/java/com/mediflow/api/domain/RefreshToken.java`:

```java
package com.mediflow.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private boolean revocado;
}
```

- [ ] **Step 6: Crear los repositorios**

Crear `medi-java/src/main/java/com/mediflow/api/repository/UsuarioRepository.java`:

```java
package com.mediflow.api.repository;

import com.mediflow.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Crear `medi-java/src/main/java/com/mediflow/api/repository/RefreshTokenRepository.java`:

```java
package com.mediflow.api.repository;

import com.mediflow.api.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHashAndRevocadoFalse(String tokenHash);
}
```

- [ ] **Step 7: Crear la migración V2 (esquema + seed de ADMIN de arranque)**

Crear `medi-java/src/main/resources/db/migration/V2__auth_schema.sql`:

```sql
CREATE TABLE usuarios (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(150) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('PACIENTE', 'PROFESIONAL', 'ADMIN')),
    paciente_id    BIGINT REFERENCES pacientes(id),
    profesional_id BIGINT REFERENCES profesionales(id),
    activo         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_usuario_vinculo_segun_rol CHECK (
        (rol = 'PACIENTE'    AND paciente_id IS NOT NULL AND profesional_id IS NULL) OR
        (rol = 'PROFESIONAL' AND profesional_id IS NOT NULL AND paciente_id IS NULL) OR
        (rol = 'ADMIN'       AND paciente_id IS NULL AND profesional_id IS NULL)
    )
);

-- Un Paciente/Profesional no puede tener mas de un Usuario asociado (vinculo 1 a 1).
CREATE UNIQUE INDEX uq_usuario_paciente_id ON usuarios (paciente_id) WHERE paciente_id IS NOT NULL;
CREATE UNIQUE INDEX uq_usuario_profesional_id ON usuarios (profesional_id) WHERE profesional_id IS NOT NULL;

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios(id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ  NOT NULL,
    revocado    BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX ix_refresh_tokens_usuario_id ON refresh_tokens (usuario_id);

-- pgcrypto: solo se usa aca, una vez, para generar el hash BCrypt del ADMIN semilla
-- sin tener que pegar un hash calculado a mano (riesgo de copiar mal el string).
-- crypt()/gen_salt('bf') produce el mismo formato $2a$ que BCryptPasswordEncoder.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ADMIN de arranque: sin esto no hay forma de crear el primer ADMIN, porque
-- POST /api/v1/auth/registro con rol=ADMIN exige ya estar autenticado como ADMIN.
-- Password de desarrollo: 'admin1234' (cambiar credenciales reales en produccion).
INSERT INTO usuarios (email, password_hash, rol, activo)
VALUES ('admin@mediflow.cl', crypt('admin1234', gen_salt('bf', 10)), 'ADMIN', true);
```

- [ ] **Step 8: Verificar que compila**

Run: `cd medi-java && mvn -q compile`
Expected: sin salida (compila sin errores). Si falla por dependencias no descargadas, correr sin `-o`/offline.

- [ ] **Step 9: Commit**

```bash
git add medi-java/pom.xml medi-java/src/main/resources/application.yml medi-java/src/main/resources/db/migration/V2__auth_schema.sql medi-java/src/main/java/com/mediflow/api/domain/Rol.java medi-java/src/main/java/com/mediflow/api/domain/Usuario.java medi-java/src/main/java/com/mediflow/api/domain/RefreshToken.java medi-java/src/main/java/com/mediflow/api/repository/UsuarioRepository.java medi-java/src/main/java/com/mediflow/api/repository/RefreshTokenRepository.java
git commit -m "feat: agrega modelo de datos de autenticacion (Usuario, RefreshToken) y dependencias JWT"
```

---

### Task 2: JwtService (TDD)

**Files:**
- Create: `medi-java/src/main/java/com/mediflow/api/exception/JwtInvalidoException.java`
- Create: `medi-java/src/main/java/com/mediflow/api/security/JwtService.java`
- Test: `medi-java/src/test/java/com/mediflow/api/security/JwtServiceTest.java`

**Interfaces:**
- Consumes: `Rol` (Task 1).
- Produces: `JwtService(String secret, long accessTokenMinutos)` constructor; `generarAccessToken(Long usuarioId, String email, Rol rol, Long pacienteId, Long profesionalId): String`; `validarYObtenerClaims(String token): io.jsonwebtoken.Claims` (lanza `JwtInvalidoException` si el token es invalido/esta expirado/malformado). Claims numericos (`usuarioId`, `pacienteId`, `profesionalId`) se leen con `claims.get(nombre, Number.class).longValue()` — nunca con `Long.class` directo, porque Jackson puede deserializar un numero pequeño como `Integer` y `Claims.get(key, Long.class)` lanza `ClassCastException` en ese caso. Esto lo consume el Task 3 (`JwtAuthFilter`).

- [ ] **Step 1: Crear la excepción JwtInvalidoException**

Crear `medi-java/src/main/java/com/mediflow/api/exception/JwtInvalidoException.java`:

```java
package com.mediflow.api.exception;

public class JwtInvalidoException extends RuntimeException {
    public JwtInvalidoException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Escribir el test que falla**

Crear `medi-java/src/test/java/com/mediflow/api/security/JwtServiceTest.java`:

```java
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
```

- [ ] **Step 3: Correr el test para confirmar que falla**

Run: `cd medi-java && mvn -q test -Dtest=JwtServiceTest`
Expected: FAIL — `JwtService` no existe todavía (error de compilación).

- [ ] **Step 4: Implementar JwtService**

Crear `medi-java/src/main/java/com/mediflow/api/security/JwtService.java`:

```java
package com.mediflow.api.security;

import com.mediflow.api.domain.Rol;
import com.mediflow.api.exception.JwtInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
        } catch (ExpiredJwtException | JwtException ex) {
            throw new JwtInvalidoException("Token invalido o expirado");
        }
    }
}
```

- [ ] **Step 5: Correr el test para confirmar que pasa**

Run: `cd medi-java && mvn -q test -Dtest=JwtServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/exception/JwtInvalidoException.java medi-java/src/main/java/com/mediflow/api/security/JwtService.java medi-java/src/test/java/com/mediflow/api/security/JwtServiceTest.java
git commit -m "feat: agrega JwtService para generar y validar access tokens"
```

---

### Task 3: UsuarioPrincipal + JwtAuthFilter

**Files:**
- Create: `medi-java/src/main/java/com/mediflow/api/security/UsuarioPrincipal.java`
- Create: `medi-java/src/main/java/com/mediflow/api/security/JwtAuthFilter.java`

**Interfaces:**
- Consumes: `JwtService.validarYObtenerClaims` (Task 2), `Rol` (Task 1).
- Produces: `UsuarioPrincipal` (implementa `UserDetails`; getters `getUsuarioId()`, `getRol()`, `getPacienteId()`, `getProfesionalId()`, `getUsername()` = email) — lo consume el Task 9 (`RecursoAuthService`). `JwtAuthFilter` (bean `@Component`, extiende `OncePerRequestFilter`) — lo consume el Task 4 (`SecurityConfig`, via `addFilterBefore`). Si el token es invalido, `JwtAuthFilter` deja `request.setAttribute("jwtError", mensaje)` para que el `AuthenticationEntryPoint` del Task 4 lo use.

No hay test dedicado para este paso: no tiene lógica de negocio aislable de Spring Security (se verifica indirectamente en el Task 10, `AuthIntegrationTest`, que ejercita el filtro contra un endpoint real).

- [ ] **Step 1: Crear UsuarioPrincipal**

Crear `medi-java/src/main/java/com/mediflow/api/security/UsuarioPrincipal.java`:

```java
package com.mediflow.api.security;

import com.mediflow.api.domain.Rol;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UsuarioPrincipal implements UserDetails {

    private final Long usuarioId;
    private final String email;
    private final Rol rol;
    private final Long pacienteId;
    private final Long profesionalId;

    public UsuarioPrincipal(Long usuarioId, String email, Rol rol, Long pacienteId, Long profesionalId) {
        this.usuarioId = usuarioId;
        this.email = email;
        this.rol = rol;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        // No aplica: la autenticacion ya ocurrio al validar el JWT, no se vuelve a
        // chequear password en cada request.
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
```

- [ ] **Step 2: Crear JwtAuthFilter**

Crear `medi-java/src/main/java/com/mediflow/api/security/JwtAuthFilter.java`:

```java
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
            } catch (JwtInvalidoException ex) {
                // Token invalido/expirado: se deja la request sin autenticar. El
                // AuthenticationEntryPoint (SecurityConfig) decide el 401 mas abajo si
                // el endpoint lo requiere — no se corta la cadena aca para no romper
                // rutas publicas si llega un header Authorization basura.
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
```

- [ ] **Step 3: Verificar que compila**

Run: `cd medi-java && mvn -q compile`
Expected: sin salida (compila sin errores).

- [ ] **Step 4: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/security/UsuarioPrincipal.java medi-java/src/main/java/com/mediflow/api/security/JwtAuthFilter.java
git commit -m "feat: agrega UsuarioPrincipal y el filtro JWT que autentica cada request"
```

---

### Task 4: SecurityConfig

**Files:**
- Create: `medi-java/src/main/java/com/mediflow/api/security/SecurityConfig.java`

**Interfaces:**
- Consumes: `JwtAuthFilter` (Task 3), `ErrorResponse` (ya existe en `com.mediflow.api.exception`).
- Produces: bean `PasswordEncoder` (`BCryptPasswordEncoder`) — lo consume el Task 6 (`AuthServiceImpl`). Habilita `@PreAuthorize` vía `@EnableMethodSecurity` — lo consume el Task 9.

- [ ] **Step 1: Crear SecurityConfig**

Crear `medi-java/src/main/java/com/mediflow/api/security/SecurityConfig.java`:

```java
package com.mediflow.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.api.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(this::escribirRespuesta401))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void escribirRespuesta401(jakarta.servlet.http.HttpServletRequest request,
                                       HttpServletResponse response,
                                       org.springframework.security.core.AuthenticationException authException)
            throws java.io.IOException {
        String mensaje = (String) request.getAttribute("jwtError");
        if (mensaje == null) {
            mensaje = "Se requiere un token valido";
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of(401, "Unauthorized", mensaje)));
    }
}
```

- [ ] **Step 2: Verificar que compila**

Run: `cd medi-java && mvn -q compile`
Expected: sin salida (compila sin errores).

- [ ] **Step 3: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/security/SecurityConfig.java
git commit -m "feat: agrega SecurityConfig con filtro JWT stateless y respuesta 401 uniforme"
```

---

### Task 5: DTOs de auth y contrato de AuthService

**Files:**
- Create: `medi-java/src/main/java/com/mediflow/api/dto/auth/RegistroRequest.java`
- Create: `medi-java/src/main/java/com/mediflow/api/dto/auth/LoginRequest.java`
- Create: `medi-java/src/main/java/com/mediflow/api/dto/auth/LoginResponse.java`
- Create: `medi-java/src/main/java/com/mediflow/api/dto/auth/RefreshRequest.java`
- Create: `medi-java/src/main/java/com/mediflow/api/dto/auth/UsuarioResponse.java`
- Create: `medi-java/src/main/java/com/mediflow/api/service/AuthService.java`

**Interfaces:**
- Produces: los 5 records de DTO y la interfaz `AuthService` con `registrar(RegistroRequest): UsuarioResponse`, `login(LoginRequest): LoginResponse`, `refrescar(RefreshRequest): LoginResponse`, `logout(RefreshRequest): void`. Los consumen el Task 6 (`AuthServiceImpl`) y el Task 7 (`AuthController`).

- [ ] **Step 1: Crear los DTOs**

Crear `medi-java/src/main/java/com/mediflow/api/dto/auth/RegistroRequest.java`:

```java
package com.mediflow.api.dto.auth;

import com.mediflow.api.domain.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
        @NotNull Rol rol,
        Long pacienteId,
        Long profesionalId
) {
}
```

Crear `medi-java/src/main/java/com/mediflow/api/dto/auth/LoginRequest.java`:

```java
package com.mediflow.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
```

Crear `medi-java/src/main/java/com/mediflow/api/dto/auth/LoginResponse.java`:

```java
package com.mediflow.api.dto.auth;

public record LoginResponse(String accessToken, String refreshToken, long expiraEnSegundos) {
}
```

Crear `medi-java/src/main/java/com/mediflow/api/dto/auth/RefreshRequest.java`:

```java
package com.mediflow.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {
}
```

Crear `medi-java/src/main/java/com/mediflow/api/dto/auth/UsuarioResponse.java`:

```java
package com.mediflow.api.dto.auth;

import com.mediflow.api.domain.Rol;

public record UsuarioResponse(Long id, String email, Rol rol) {
}
```

- [ ] **Step 2: Crear la interfaz AuthService**

Crear `medi-java/src/main/java/com/mediflow/api/service/AuthService.java`:

```java
package com.mediflow.api.service;

import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RefreshRequest;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;

public interface AuthService {
    UsuarioResponse registrar(RegistroRequest request);
    LoginResponse login(LoginRequest request);
    LoginResponse refrescar(RefreshRequest request);
    void logout(RefreshRequest request);
}
```

- [ ] **Step 3: Verificar que compila**

Run: `cd medi-java && mvn -q compile`
Expected: sin salida (compila sin errores).

- [ ] **Step 4: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/dto/auth medi-java/src/main/java/com/mediflow/api/service/AuthService.java
git commit -m "feat: agrega DTOs y contrato de AuthService"
```

---

### Task 6: AuthServiceImpl (TDD)

**Files:**
- Create: `medi-java/src/main/java/com/mediflow/api/service/impl/AuthServiceImpl.java`
- Test: `medi-java/src/test/java/com/mediflow/api/service/AuthServiceImplTest.java`

**Interfaces:**
- Consumes: `UsuarioRepository`, `RefreshTokenRepository` (Task 1), `PacienteRepository`, `ProfesionalRepository` (ya existen), `PasswordEncoder` (Task 4), `JwtService` (Task 2), `AuthService`, DTOs de auth (Task 5).
- Produces: `AuthServiceImpl` implementa `AuthService` — lo consume el Task 7 (`AuthController`) via inyección de la interfaz.

- [ ] **Step 1: Escribir el test que falla**

Crear `medi-java/src/test/java/com/mediflow/api/service/AuthServiceImplTest.java`:

```java
package com.mediflow.api.service;

import com.mediflow.api.domain.Rol;
import com.mediflow.api.domain.Usuario;
import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.repository.RefreshTokenRepository;
import com.mediflow.api.repository.UsuarioRepository;
import com.mediflow.api.security.JwtService;
import com.mediflow.api.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario "puro": sin Spring, sin base de datos — solo mockea los repositorios,
// igual patron que TurnoServiceImplTest.
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenMinutos", 15L);
        ReflectionTestUtils.setField(authService, "refreshTokenDias", 7L);
    }

    // SecurityContextHolder es un ThreadLocal estatico: si un test deja una
    // autenticacion cargada (o la limpia), eso puede filtrarse a otra clase de test
    // que corra en el mismo hilo (Surefire reusa hilos entre clases). Se limpia
    // siempre al terminar para que ningun otro test dependa del orden de ejecucion.
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_conCredencialesValidas_devuelveTokens() {
        Usuario usuario = Usuario.builder()
                .id(1L).email("ana@test.cl").passwordHash("hash").rol(Rol.PACIENTE).pacienteId(10L).activo(true).build();
        when(usuarioRepository.findByEmail("ana@test.cl")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave123", "hash")).thenReturn(true);
        when(jwtService.generarAccessToken(1L, "ana@test.cl", Rol.PACIENTE, 10L, null)).thenReturn("token-jwt");

        LoginResponse response = authService.login(new LoginRequest("ana@test.cl", "clave123"));

        assertThat(response.accessToken()).isEqualTo("token-jwt");
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void login_conPasswordIncorrecto_lanzaBadCredentials() {
        Usuario usuario = Usuario.builder()
                .id(1L).email("ana@test.cl").passwordHash("hash").rol(Rol.PACIENTE).pacienteId(10L).activo(true).build();
        when(usuarioRepository.findByEmail("ana@test.cl")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@test.cl", "mala")))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_conEmailInexistente_lanzaBadCredentials() {
        when(usuarioRepository.findByEmail("nadie@test.cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@test.cl", "clave123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void registrar_conEmailDuplicado_lanzaConflicto() {
        when(usuarioRepository.existsByEmail("ana@test.cl")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("ana@test.cl", "clave1234", Rol.PACIENTE, 10L, null)))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_conPacienteInexistente_lanzaResourceNotFound() {
        when(usuarioRepository.existsByEmail("ana@test.cl")).thenReturn(false);
        when(pacienteRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("ana@test.cl", "clave1234", Rol.PACIENTE, 99L, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_comoAdminSinEstarAutenticadoComoAdmin_lanzaAccesoDenegado() {
        // Nadie autenticado en el SecurityContext (caso: llamada anonima a /registro).
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("nuevo-admin@test.cl", "clave1234", Rol.ADMIN, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(usuarioRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Correr el test para confirmar que falla**

Run: `cd medi-java && mvn -q test -Dtest=AuthServiceImplTest`
Expected: FAIL — `AuthServiceImpl` no existe todavía (error de compilación).

- [ ] **Step 3: Implementar AuthServiceImpl**

Crear `medi-java/src/main/java/com/mediflow/api/service/impl/AuthServiceImpl.java`:

```java
package com.mediflow.api.service.impl;

import com.mediflow.api.domain.RefreshToken;
import com.mediflow.api.domain.Rol;
import com.mediflow.api.domain.Usuario;
import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RefreshRequest;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.JwtInvalidoException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.repository.RefreshTokenRepository;
import com.mediflow.api.repository.UsuarioRepository;
import com.mediflow.api.security.JwtService;
import com.mediflow.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${mediflow.jwt.access-token-minutos}")
    private long accessTokenMinutos;

    @Value("${mediflow.jwt.refresh-token-dias}")
    private long refreshTokenDias;

    @Override
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        // El registro publico (sin token) solo puede crear cuentas PACIENTE. Crear
        // PROFESIONAL o ADMIN exige ya estar autenticado como ADMIN — si no, cualquier
        // anonimo podria auto-otorgarse rol ADMIN llamando este mismo endpoint publico.
        if (request.rol() != Rol.PACIENTE && !solicitanteEsAdmin()) {
            throw new AccessDeniedException(
                    "Solo un administrador puede registrar cuentas de PROFESIONAL o ADMIN");
        }

        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflictoDeNegocioException("Ya existe un usuario con el email " + request.email());
        }
        validarVinculoDeDominio(request.rol(), request.pacienteId(), request.profesionalId());

        Usuario usuario = Usuario.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .pacienteId(request.pacienteId())
                .profesionalId(request.profesionalId())
                .activo(true)
                .build();
        usuario = usuarioRepository.save(usuario);
        return new UsuarioResponse(usuario.getId(), usuario.getEmail(), usuario.getRol());
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Email o contraseña incorrectos");
        }

        return generarTokens(usuario);
    }

    @Override
    @Transactional
    public LoginResponse refrescar(RefreshRequest request) {
        String hash = hashSha256(request.refreshToken());
        RefreshToken guardado = refreshTokenRepository.findByTokenHashAndRevocadoFalse(hash)
                .orElseThrow(() -> new JwtInvalidoException("Refresh token invalido o revocado"));

        if (guardado.getExpiraEn().isBefore(Instant.now())) {
            throw new JwtInvalidoException("Refresh token expirado");
        }

        Usuario usuario = usuarioRepository.findById(guardado.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario " + guardado.getUsuarioId() + " no encontrado"));

        // Rotacion: el refresh token usado se revoca y se emite uno nuevo. Si alguien
        // reutiliza un refresh token ya canjeado, ese segundo intento falla (ya revocado).
        guardado.setRevocado(true);
        refreshTokenRepository.save(guardado);

        return generarTokens(usuario);
    }

    @Override
    @Transactional
    public void logout(RefreshRequest request) {
        String hash = hashSha256(request.refreshToken());
        refreshTokenRepository.findByTokenHashAndRevocadoFalse(hash).ifPresent(token -> {
            token.setRevocado(true);
            refreshTokenRepository.save(token);
        });
    }

    private LoginResponse generarTokens(Usuario usuario) {
        String accessToken = jwtService.generarAccessToken(
                usuario.getId(), usuario.getEmail(), usuario.getRol(), usuario.getPacienteId(), usuario.getProfesionalId());

        String refreshTokenPlano = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .usuarioId(usuario.getId())
                .tokenHash(hashSha256(refreshTokenPlano))
                .expiraEn(Instant.now().plusSeconds(refreshTokenDias * 24 * 60 * 60))
                .revocado(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, refreshTokenPlano, accessTokenMinutos * 60);
    }

    private void validarVinculoDeDominio(Rol rol, Long pacienteId, Long profesionalId) {
        switch (rol) {
            case PACIENTE -> {
                if (pacienteId == null || !pacienteRepository.existsById(pacienteId)) {
                    throw new ResourceNotFoundException("Paciente " + pacienteId + " no encontrado");
                }
            }
            case PROFESIONAL -> {
                if (profesionalId == null || !profesionalRepository.existsById(profesionalId)) {
                    throw new ResourceNotFoundException("Profesional " + profesionalId + " no encontrado");
                }
            }
            case ADMIN -> {
                // Sin vinculo de dominio.
            }
        }
    }

    private boolean solicitanteEsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String hashSha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
```

- [ ] **Step 4: Correr el test para confirmar que pasa**

Run: `cd medi-java && mvn -q test -Dtest=AuthServiceImplTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/service/impl/AuthServiceImpl.java medi-java/src/test/java/com/mediflow/api/service/AuthServiceImplTest.java
git commit -m "feat: implementa AuthServiceImpl (registro, login, refresh, logout)"
```

---

### Task 7: AuthController

**Files:**
- Create: `medi-java/src/main/java/com/mediflow/api/controller/AuthController.java`

**Interfaces:**
- Consumes: `AuthService` (Task 5/6), DTOs de auth (Task 5).
- Produces: endpoints `POST /api/v1/auth/registro`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` — los consume el Task 10 (`AuthIntegrationTest`).

- [ ] **Step 1: Crear AuthController**

Crear `medi-java/src/main/java/com/mediflow/api/controller/AuthController.java`:

```java
package com.mediflow.api.controller;

import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RefreshRequest;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;
import com.mediflow.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refrescar(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
}
```

- [ ] **Step 2: Verificar que compila**

Run: `cd medi-java && mvn -q compile`
Expected: sin salida (compila sin errores).

- [ ] **Step 3: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/controller/AuthController.java
git commit -m "feat: expone los endpoints de autenticacion en /api/v1/auth"
```

---

### Task 8: Extender GlobalExceptionHandler

**Files:**
- Modify: `medi-java/src/main/java/com/mediflow/api/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `JwtInvalidoException` (Task 2), `BadCredentialsException`/`AccessDeniedException` (Spring Security, ya en el classpath desde Task 1).

- [ ] **Step 1: Agregar los 3 manejadores nuevos**

En `medi-java/src/main/java/com/mediflow/api/exception/GlobalExceptionHandler.java`, agregar los imports:

```java
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
```

Y agregar estos 3 métodos dentro de la clase (después de `handleIntegridad`, antes de `handleValidacion`):

```java
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleCredencialesInvalidas(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized", "Email o contraseña incorrectos"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccesoDenegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", "No tienes permiso para esta operación"));
    }

    @ExceptionHandler(JwtInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleJwtInvalido(JwtInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized", ex.getMessage()));
    }
```

- [ ] **Step 2: Verificar que compila**

Run: `cd medi-java && mvn -q compile`
Expected: sin salida (compila sin errores).

- [ ] **Step 3: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/exception/GlobalExceptionHandler.java
git commit -m "feat: extiende GlobalExceptionHandler para errores de autenticacion"
```

---

### Task 9: RecursoAuthService y @PreAuthorize en los controllers existentes

**Files:**
- Create: `medi-java/src/main/java/com/mediflow/api/security/RecursoAuthService.java`
- Modify: `medi-java/src/main/java/com/mediflow/api/controller/PacienteController.java`
- Modify: `medi-java/src/main/java/com/mediflow/api/controller/ProfesionalController.java`
- Modify: `medi-java/src/main/java/com/mediflow/api/controller/TurnoController.java`
- Modify: `medi-java/src/main/java/com/mediflow/api/controller/DocumentoController.java`

**Interfaces:**
- Consumes: `UsuarioPrincipal` (Task 3).
- Produces: bean `recursoAuth` referenciable desde SpEL en `@PreAuthorize("@recursoAuth.esPropioPaciente(...)")`.

- [ ] **Step 1: Crear RecursoAuthService**

Crear `medi-java/src/main/java/com/mediflow/api/security/RecursoAuthService.java`:

```java
package com.mediflow.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("recursoAuth")
public class RecursoAuthService {

    public boolean esPropioPaciente(Authentication authentication, Long pacienteId) {
        if (!(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return false;
        }
        return pacienteId.equals(principal.getPacienteId());
    }

    public boolean esPropioProfesional(Authentication authentication, Long profesionalId) {
        if (!(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return false;
        }
        return profesionalId.equals(principal.getProfesionalId());
    }
}
```

- [ ] **Step 2: Anotar PacienteController**

En `medi-java/src/main/java/com/mediflow/api/controller/PacienteController.java`, agregar el import:

```java
import org.springframework.security.access.prepost.PreAuthorize;
```

Y anotar `obtenerPorId` y `eliminar`:

```java
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESIONAL','ADMIN') or @recursoAuth.esPropioPaciente(authentication, #id)")
    public PacienteResponse obtenerPorId(@PathVariable Long id) {
        return pacienteService.obtenerPorId(id);
    }
```

```java
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        pacienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 3: Anotar ProfesionalController**

En `medi-java/src/main/java/com/mediflow/api/controller/ProfesionalController.java`, agregar el mismo import (`org.springframework.security.access.prepost.PreAuthorize`) y anotar:

```java
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PACIENTE','ADMIN') or @recursoAuth.esPropioProfesional(authentication, #id)")
    public ProfesionalResponse obtenerPorId(@PathVariable Long id) {
        return profesionalService.obtenerPorId(id);
    }
```

```java
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        profesionalService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: Anotar TurnoController**

En `medi-java/src/main/java/com/mediflow/api/controller/TurnoController.java`, agregar el mismo import y anotar `cambiarEstado`:

```java
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('PROFESIONAL','ADMIN')")
    public TurnoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoTurnoRequest request) {
        return turnoService.cambiarEstado(id, request);
    }
```

- [ ] **Step 5: Anotar DocumentoController**

En `medi-java/src/main/java/com/mediflow/api/controller/DocumentoController.java`, agregar el mismo import y anotar `eliminar` (por consistencia con Paciente/Profesional — ver "Desviación deliberada" al inicio del plan):

```java
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 6: Verificar que compila**

Run: `cd medi-java && mvn -q compile`
Expected: sin salida (compila sin errores).

- [ ] **Step 7: Commit**

```bash
git add medi-java/src/main/java/com/mediflow/api/security/RecursoAuthService.java medi-java/src/main/java/com/mediflow/api/controller/PacienteController.java medi-java/src/main/java/com/mediflow/api/controller/ProfesionalController.java medi-java/src/main/java/com/mediflow/api/controller/TurnoController.java medi-java/src/main/java/com/mediflow/api/controller/DocumentoController.java
git commit -m "feat: aplica autorizacion por rol a los controllers existentes"
```

---

### Task 10: AuthIntegrationTest y verificación final

**Files:**
- Create: `medi-java/src/test/java/com/mediflow/api/AuthIntegrationTest.java`

**Interfaces:**
- Consumes: todo lo anterior (Task 1-9) contra un Postgres real (Testcontainers), mismo patrón que `TurnoIntegrationTest`.

- [ ] **Step 1: Escribir el test de integración**

Crear `medi-java/src/test/java/com/mediflow/api/AuthIntegrationTest.java`:

```java
package com.mediflow.api;

import com.mediflow.api.domain.Rol;
import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;
import com.mediflow.api.dto.paciente.PacienteRequest;
import com.mediflow.api.dto.paciente.PacienteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void endpointProtegido_sinToken_devuelve401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/pacientes/1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginComoAdmin_crearPacienteYUsuario_loginComoPaciente_yVerSuPropioRegistro() {
        // El admin semilla (V2__auth_schema.sql) es el unico usuario que existe al
        // arrancar — se usa para crear al Paciente y su cuenta.
        LoginResponse loginAdmin = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@mediflow.cl", "admin1234"), LoginResponse.class);
        assertThat(loginAdmin.accessToken()).isNotBlank();

        HttpHeaders headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(loginAdmin.accessToken());

        PacienteRequest pacienteRequest = new PacienteRequest(
                "33333333-3", "Fernanda", "Lopez",
                java.time.LocalDate.of(1990, 3, 15), "fernanda@test.cl", "922222222");
        ResponseEntity<PacienteResponse> pacienteResponse = restTemplate.exchange(
                "/api/v1/pacientes", HttpMethod.POST,
                new HttpEntity<>(pacienteRequest, headersAdmin), PacienteResponse.class);
        assertThat(pacienteResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long pacienteId = pacienteResponse.getBody().id();

        RegistroRequest registroRequest = new RegistroRequest(
                "fernanda@test.cl", "clave12345", Rol.PACIENTE, pacienteId, null);
        ResponseEntity<UsuarioResponse> registroResponse = restTemplate.postForEntity(
                "/api/v1/auth/registro", registroRequest, UsuarioResponse.class);
        assertThat(registroResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginResponse loginPaciente = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("fernanda@test.cl", "clave12345"), LoginResponse.class);
        assertThat(loginPaciente.accessToken()).isNotBlank();
        assertThat(loginPaciente.refreshToken()).isNotBlank();

        HttpHeaders headersPaciente = new HttpHeaders();
        headersPaciente.setBearerAuth(loginPaciente.accessToken());
        ResponseEntity<PacienteResponse> propioPaciente = restTemplate.exchange(
                "/api/v1/pacientes/" + pacienteId, HttpMethod.GET,
                new HttpEntity<>(headersPaciente), PacienteResponse.class);
        assertThat(propioPaciente.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(propioPaciente.getBody().id()).isEqualTo(pacienteId);
    }

    @Test
    void registrarAdmin_sinAutenticacion_devuelve403() {
        RegistroRequest registroAdmin = new RegistroRequest("otro-admin@test.cl", "clave12345", Rol.ADMIN, null, null);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/registro", registroAdmin, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

- [ ] **Step 2: Correr el test de integración**

Run: `cd medi-java && mvn test -Dtest=AuthIntegrationTest`
Expected: PASS (3 tests) — usa Testcontainers, tarda ~60-90s (levanta Postgres real + Spring Boot).

- [ ] **Step 3: Correr toda la suite completa**

Run: `cd medi-java && mvn -q test`
Expected: sin salida (todos los tests pasan — unitarios + los 2 de integración existentes).

- [ ] **Step 4: Commit final**

```bash
git add medi-java/src/test/java/com/mediflow/api/AuthIntegrationTest.java
git commit -m "test: agrega AuthIntegrationTest de punta a punta con Testcontainers"
```

---

## Verificación end-to-end (manual, opcional)

Con `docker compose up -d` en `aws-local-sandbox` no es necesario (usa su propio Postgres de Testcontainers). Para probar manualmente contra el Postgres local de desarrollo:

```bash
cd medi-java
mvn spring-boot:run
```

Y en otra terminal:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"admin@mediflow.cl","password":"admin1234"}'
```

Debería devolver un JSON con `accessToken`, `refreshToken` y `expiraEnSegundos`.
