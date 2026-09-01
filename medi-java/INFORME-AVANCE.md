# Informe de avance — MediFlow API (Día 2 del plan de 14 días)

## 1. Qué se construyó hoy

Backend completo de `medi-java` (Spring Boot 3.3.4, Java 17) con dominio Paciente/Profesional/Turno/Documento:

- **Entidades JPA** (`domain/`): `Paciente`, `Profesional`, `Turno`, `Documento`, con auditoría común (`Auditable`) y enums `EstadoTurno`/`EstadoDocumento`.
- **Migración Flyway** `V1__schema_inicial.sql`: 4 tablas con FKs, constraints `CHECK` de estado, e índice único parcial que impide que un profesional tenga dos turnos activos a la misma hora.
- **Repositorios** Spring Data JPA (uno por entidad, con métodos derivados: `existsByRut`, `existsByProfesionalIdAndFechaHoraAndEstadoNot`, etc.).
- **Mappers manuales** (`mapper/`): traducen entidad ↔ DTO, sin librería externa (MapStruct no se justifica para 4 entidades chicas).
- **Servicios** (interfaz en `service/`, implementación en `service/impl/`) con las reglas de negocio: rut único, existencia de paciente/profesional al crear un turno, no duplicar horario de un profesional, y una máquina de estados mínima para `Turno`.
- **Controladores REST** (`controller/`) bajo `/api/v1/...`, con `@Valid` en los bodies y códigos HTTP correctos.
- **Manejo de errores centralizado** (`GlobalExceptionHandler`): traduce excepciones de negocio a 404/409/400 consistentes.
- **Dockerfile multi-stage** + `.dockerignore`.
- **Swagger / OpenAPI** (`springdoc-openapi-starter-webmvc-ui`): UI en `/swagger-ui.html`, spec en `/v3/api-docs` — se agrega solo la dependencia, sin anotar nada, escanea los `@RestController` existentes.
- **Tests**: 9 tests unitarios (Mockito, sin Spring ni BD) sobre `TurnoServiceImpl` y `PacienteServiceImpl`, y un test de integración con Testcontainers (`TurnoIntegrationTest`) que ejercita el flujo completo vía HTTP contra un Postgres real.

## 2. Conceptos cubiertos por cada pieza (para repaso)

| Pieza | Concepto |
|---|---|
| `controller/*` dependiendo de `service/*Service` (interfaz) | **DIP** (Dependency Inversion, la "D" de SOLID): el controller no conoce la implementación, solo el contrato. Permite mockear el servicio en tests de controller sin levantar Spring completo. |
| `mapper/`, `service/`, `repository/` como paquetes separados | **SRP**: cada capa tiene una única razón para cambiar (forma de dato, regla de negocio, persistencia). |
| `@Transactional` a nivel de clase vs. método | Nivel de clase cuando la mayoría de los métodos escriben (Paciente/Profesional); a nivel de método cuando se mezclan lecturas y escrituras (Turno/Documento), para no envolver una lectura simple en una transacción innecesaria. |
| `@ServiceConnection` (Testcontainers + Spring Boot 3.1+) | Reemplaza el registro manual de `spring.datasource.url/username/password` vía `@DynamicPropertySource` — Spring detecta el contenedor y configura el `DataSource` solo. |
| Índice único parcial en Postgres (`WHERE estado <> 'CANCELADO'`) | Por qué el test de integración usa Postgres real (Testcontainers) y no H2: esta sintaxis es específica de Postgres. |
| Máquina de estados de `Turno` como `Map<EstadoTurno, Set<EstadoTurno>>` | Cuándo NO usar el patrón State: con 4 valores fijos y transiciones simples, una tabla de datos es más simple y igual de correcta que un jerarquía de clases. |
| Dockerfile multi-stage | Separar la etapa que compila (necesita JDK + Maven) de la que ejecuta (solo necesita JRE) reduce drásticamente el tamaño y la superficie de ataque de la imagen final. |
| `open-in-view: false` en `application.yml` | Evita el antipatrón Open Session In View, que esconde problemas N+1 al permitir lazy-loading dentro de la vista/serialización. |

## 3. Decisiones de diseño y trade-offs

1. **Mappers en paquete propio (`mapper/`)**, no dentro de `dto/`: el DTO es forma de dato, el mapeo es comportamiento (SRP).
2. **`Documento` como recurso plano** (`/api/v1/documentos`, no anidado bajo `/turnos/{id}/documentos`): el propio `DocumentoRequest` ya lleva `turnoId` en el body, igual patrón que `Turno` con `pacienteId`/`profesionalId`. Anidar duplicaría el id en path y body.
3. **Sin `CrudService<T,ID>` genérico**: las firmas reales difieren entre las 4 entidades (`Turno` no tiene `actualizar`/`eliminar`, `Documento` no tiene `actualizar`). Forzar una interfaz común habría sido sobre-ingeniería para 4 casos.
4. **Sin MapStruct**: para 4 entidades chicas, el mapeo manual es más simple de leer y depurar que configurar un generador de código.
5. **`cambiarEstado` también en `Documento`** (agregado en la revisión de diseño, ver sección 7): mismo patrón que `Turno` — `Map<EstadoDocumento, Set<EstadoDocumento>>` en vez de un patrón State. `PENDIENTE → PROCESADO | ERROR`; `PROCESADO`/`ERROR` son terminales porque un documento fallido se resube (nuevo registro), no se reintenta in-place sobre el mismo.
6. **`DataIntegrityViolationException` → 409 en `GlobalExceptionHandler`** (agregado en la revisión de diseño): el chequeo `existsByRut` / `existsByProfesionalIdAndFechaHoraAndEstadoNot` en el service tiene una ventana de carrera entre el `exists` y el `save` — dos requests concurrentes pueden pasar ambos el chequeo antes de que cualquiera haga `save`. El constraint único real (rut, índice parcial de turno) en Postgres sigue siendo la fuente de verdad y evita el dato duplicado, pero sin este handler la carrera perdida devolvía un 500 crudo con detalle de SQL en vez de un 409 consistente con el que ya se devuelve cuando el service gana la carrera.

## 4. Comparación real de tamaño de imagen Docker

| Imagen | Tamaño | Contenido |
|---|---|---|
| `mediflow-api:v1` (multi-stage, `eclipse-temurin:17-jre-alpine`) | **353 MB** | Solo el JRE + el jar de la app |
| `mediflow-api:single-stage` (`maven:3.9-eclipse-temurin-17` en una sola etapa) | **1.35 GB** | JDK completo + Maven + repositorio `.m2` + código fuente + el jar |

**Reducción: ~74% (casi 4x más chica)**. Este es exactamente el dato citable en entrevista que menciona el plan de 14 días.

## 5. Pendientes explícitos

Resueltos en la revisión de diseño (ver sección 7):

- ~~Tests unitarios de `ProfesionalServiceImpl` y `DocumentoServiceImpl`~~ → creados, replicando el patrón de `PacienteServiceImplTest`/`TurnoServiceImplTest`.
- ~~Posible `cambiarEstado` para `Documento`~~ → agregado (`PATCH /api/v1/documentos/{id}/estado`), mismo patrón de máquina de estados que `Turno`.
- ~~Inconsistencia de auditoría en los DTOs~~ → los 4 responses exponen ahora `creadoEn`.

Siguen pendientes:

- **Front en React**: no se construyó en esta fase — se hace después de que el contrato de la API esté estable, para no maquetar contra endpoints que aún podrían cambiar.
- **Infraestructura AWS (LocalStack/Terraform) y GCP**: corresponde a los Días 3 en adelante del plan de 14 días.
- **Test de integración con Testcontainers no se pudo ejecutar en este entorno**: Testcontainers 1.20.1 (dependencia usada en el `pom.xml`) tiene un problema de negociación de versión del cliente Docker contra Docker Desktop en Windows/npipe (el pull de la imagen de Postgres falla con `client version 1.32 is too old`). El código del test es correcto (se revisó su estructura y compila); es un problema de compatibilidad de herramientas, no del código de MediFlow. En la revisión de diseño se confirmó además que, en esta sesión, el daemon de Docker Desktop ni siquiera estaba corriendo (`docker info` falla al conectar al pipe `dockerDesktopLinuxEngine`), así que no fue posible probar si un bump de `testcontainers-bom` soluciona la negociación de versión — no se tocó esa dependencia sin poder verificarlo. Sigue siendo la solución candidata a futuro, a validar con Docker Desktop corriendo.

## 6. Cómo correr y probar localmente

```bash
# 1. Levantar Postgres local (ya existe en el repo)
cd ../aws-local-sandbox
docker compose up -d postgres

# 2. Compilar y correr los tests unitarios (rápidos, sin Docker)
cd ../medi-java
mvn test -Dtest='PacienteServiceImplTest,TurnoServiceImplTest,ProfesionalServiceImplTest,DocumentoServiceImplTest'

# 3. Levantar la API
mvn spring-boot:run

# 4. Construir y correr en Docker, conectado a la red de docker-compose (recomendado):
#    el contenedor resuelve "postgres" por nombre de servicio, sin depender de host.docker.internal.
#    "aws-local-sandbox_default" es la red que docker compose crea sola a partir del
#    nombre de la carpeta — confirmar con `docker network ls` si se renombró el proyecto.
docker build -t mediflow-api:v1 .
docker run -p 8081:8080 --network aws-local-sandbox_default \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mediflow \
  mediflow-api:v1

# Alternativa si el contenedor no puede unirse a esa red (compose no está levantado,
# o el nombre de red no coincide): usar el gateway del host en vez del nombre del
# servicio. application.yml trae "localhost" como default, que dentro del contenedor
# apunta al contenedor mismo — por eso acá también hay que pisar SPRING_DATASOURCE_URL.
docker run -p 8081:8080 --add-host=host.docker.internal:host-gateway \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/mediflow \
  mediflow-api:v1

# Si el puerto de host (8081) ya está ocupado, cambiar solo el primer número del -p
# (el segundo es el puerto interno del contenedor, 8080, y no cambia):
docker run -p 8082:8080 --network aws-local-sandbox_default \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mediflow \
  mediflow-api:v1
```

Con la API corriendo, la documentación interactiva (Swagger UI) queda en
`http://localhost:<puerto-host>/swagger-ui.html` (ej. `:8081/swagger-ui.html` con
los comandos de arriba); el spec OpenAPI crudo en `/v3/api-docs`.

## 7. Actualización — revisión de diseño y cierre de pendientes

Se pidió retomar este informe, validar/mejorar el diseño y trade-offs de la sección 3, y cerrar los pendientes explícitos de la sección 5 que estuvieran dentro del alcance de `medi-java` (no front, no infra).

**Diseño:**

- `GlobalExceptionHandler` ahora traduce `DataIntegrityViolationException` a 409, cerrando la ventana de carrera entre los chequeos `existsByX` de los services y los constraints únicos reales de Postgres (ver punto 6 de la sección 3).

**Pendientes cerrados:**

- `ProfesionalServiceImplTest` y `DocumentoServiceImplTest` — 3 y 6 tests respectivamente, mismo estilo Mockito puro que los existentes.
- `cambiarEstado` para `Documento`: nuevo DTO `CambioEstadoDocumentoRequest`, método en `DocumentoService`/`DocumentoServiceImpl` con máquina de estados (`PENDIENTE → PROCESADO | ERROR`, terminales `PROCESADO`/`ERROR`), endpoint `PATCH /api/v1/documentos/{id}/estado`.
- `creadoEn` agregado a `ProfesionalResponse`, `TurnoResponse` y `DocumentoResponse` (antes solo lo exponía `PacienteResponse`), poblado desde `Auditable.createdAt` en cada mapper.

**Pendiente que sigue abierto:** el problema de Testcontainers en Windows — se confirmó que el daemon de Docker Desktop no estaba activo en esta sesión, por lo que no se pudo intentar ni validar un fix (ver detalle en sección 5). No se modificó `testcontainers-bom` sin poder verificarlo.

**Verificación:** `mvn -o compile` sin errores; `mvn -o test -Dtest='PacienteServiceImplTest,TurnoServiceImplTest,ProfesionalServiceImplTest,DocumentoServiceImplTest'` → `Tests run: 18, Failures: 0, Errors: 0` (`BUILD SUCCESS`).
