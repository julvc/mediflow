# Autenticación, roles y tokens JWT en medi-java

## Contexto

`medi-java` (Spring Boot 3.3.4, Java 17) tiene el CRUD completo de Paciente/Profesional/Turno/Documento, probado (18 tests unitarios + `TurnoIntegrationTest` con Testcontainers, todos en verde), pero sin ningún mecanismo de autenticación: cualquiera puede llamar cualquier endpoint.

Este trabajo agrega autenticación (login), autorización por rol (RBAC) y tokens de acceso (JWT), con dos objetivos explícitos del usuario:

1. Es un prerrequisito real para el frontend: antes de diseñar pantallas de login/perfil hace falta un contrato de API estable (qué devuelve el login, qué roles existen, cómo se usa el token).
2. Es contenido de estudio para entrevistas de trabajo — el usuario quiere entender y poder explicar cada pieza (por qué JWT, por qué separar `Usuario` del dominio, por qué access+refresh token), no solo tener el código funcionando. Esto también alimenta el documento de estudio (Artifact) acordado por separado.

Decisiones ya tomadas con el usuario durante el brainstorming:

- Orden de trabajo: backend de auth primero, frontend después (sobre el contrato ya definido).
- Roles de dominio reales: `PACIENTE`, `PROFESIONAL`, `ADMIN` (no roles genéricos tipo USER/ADMIN).
- Modelo de usuario: entidad `Usuario` separada del dominio, vinculada opcionalmente a `Paciente` o `Profesional` (ninguna de las dos para `ADMIN`).
- Estrategia de tokens: access token + refresh token (no solo access token).
- Enfoque de implementación: filtro JWT manual (`OncePerRequestFilter` + librería `jjwt`), no Spring Security OAuth2 Resource Server — porque este servicio es a la vez emisor y validador de sus propios tokens (no hay un Authorization Server externo), y el enfoque manual deja cada paso explicable en una entrevista.

## Modelo de datos

Migración Flyway nueva: `V2__auth_schema.sql`.

**Tabla `usuario`**
- `id` (PK)
- `email` (único, not null)
- `password_hash` (BCrypt, not null)
- `rol` (`PACIENTE` | `PROFESIONAL` | `ADMIN`, not null)
- `paciente_id` (FK nullable a `paciente`)
- `profesional_id` (FK nullable a `profesional`)
- `activo` (boolean, default true)
- columnas de auditoría (reusar el patrón `Auditable` ya existente en el dominio)
- `CHECK`: si `rol = 'PACIENTE'` entonces `paciente_id` no es nulo y `profesional_id` es nulo; si `rol = 'PROFESIONAL'` es al revés; si `rol = 'ADMIN'` ambos son nulos. Esto evita en la base de datos (no solo en código) que un usuario quede con un rol inconsistente con su vínculo de dominio.

**Tabla `refresh_token`**
- `id` (PK)
- `usuario_id` (FK a `usuario`, not null)
- `token_hash` (SHA-256 del refresh token, único, not null — se guarda el hash, no el token en texto plano, para que una fuga de la base de datos no exponga tokens usables directamente)
- `expira_en` (timestamp, not null)
- `revocado` (boolean, default false)

## Endpoints nuevos

Todos bajo `/api/v1/auth`, públicos (no requieren JWT), el resto de la API sí lo requiere salvo este prefijo:

- `POST /api/v1/auth/registro` — crea un `Usuario`. Si el rol es `PACIENTE`/`PROFESIONAL`, requiere el id de un Paciente/Profesional existente para vincular (no crea el registro de dominio, solo lo vincula).
- `POST /api/v1/auth/login` — valida email/password (BCrypt), devuelve `accessToken` (expira en 15 min) + `refreshToken` (expira en 7 días, persistido en `refresh_token`).
- `POST /api/v1/auth/refresh` — recibe un `refreshToken` vigente y no revocado, devuelve un `accessToken` nuevo.
- `POST /api/v1/auth/logout` — marca el `refreshToken` recibido como `revocado = true`.

## Autorización por rol

Se anota con `@PreAuthorize` sobre los controllers ya existentes (no se tocan las reglas de negocio de los services, solo el borde HTTP):

- `TurnoController` — `PATCH /{id}/estado` solo `PROFESIONAL` o `ADMIN`.
- `PacienteController` / `ProfesionalController` — `DELETE` solo `ADMIN`.
- `GET` de un recurso propio (ej. un `PACIENTE` viendo su propio registro) permitido para el dueño, `PROFESIONAL`/`ADMIN` sin restricción — se resuelve comparando el `usuario_id` del JWT contra el `paciente_id`/`profesional_id` del recurso.

`SecurityFilterChain`: `/api/v1/auth/**` y `/swagger-ui/**`/`/v3/api-docs/**` públicos; todo lo demás exige JWT válido vía el filtro manual.

## Manejo de errores

Se extiende el `GlobalExceptionHandler` ya existente (no se crea uno nuevo):

- `BadCredentialsException` → 401 (login con email/password inválido).
- `AccessDeniedException` → 403 (rol sin permiso para la operación).
- Token JWT expirado o inválido (excepción propia del filtro, ej. `JwtInvalidoException`) → 401 con mensaje distinto al de credenciales inválidas, para poder diferenciar en el frontend "tu sesión expiró" de "contraseña incorrecta".

## Testing

- `JwtServiceTest` (unitario): generación, validación, y expiración de tokens.
- `AuthServiceImplTest` (unitario, Mockito): login exitoso, login con credenciales inválidas, registro con email duplicado, registro con vínculo a Paciente/Profesional inexistente.
- `AuthIntegrationTest` (Testcontainers, mismo patrón que `TurnoIntegrationTest`): login real de punta a punta, uso del `accessToken` contra un endpoint protegido existente (ej. `GET /api/v1/turnos/{id}`), y verificación de 401 al llamar sin token o con uno expirado.

## Fuera de alcance de este spec

- El frontend (login, manejo de tokens en el cliente) — sub-proyecto separado, se brainstorm-ea después de que este backend quede implementado.
- El documento de estudio para entrevistas (Artifact) — se arma por secciones a medida que cada pieza queda lista, no es parte de este spec.
- Infraestructura AWS/GCP — sigue fuera de alcance según CLAUDE.md.
