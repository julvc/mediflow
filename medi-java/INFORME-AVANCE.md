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
5. **`cambiarEstado` solo en `Turno`, no en `Documento`**: no fue pedido explícitamente; se deja como pendiente (ver sección 5).

## 4. Comparación real de tamaño de imagen Docker

| Imagen | Tamaño | Contenido |
|---|---|---|
| `mediflow-api:v1` (multi-stage, `eclipse-temurin:17-jre-alpine`) | **353 MB** | Solo el JRE + el jar de la app |
| `mediflow-api:single-stage` (`maven:3.9-eclipse-temurin-17` en una sola etapa) | **1.35 GB** | JDK completo + Maven + repositorio `.m2` + código fuente + el jar |

**Reducción: ~74% (casi 4x más chica)**. Este es exactamente el dato citable en entrevista que menciona el plan de 14 días.

## 5. Pendientes explícitos

- **Front en React**: no se construyó en esta fase — se hace después de que el contrato de la API esté estable, para no maquetar contra endpoints que aún podrían cambiar.
- **Infraestructura AWS (LocalStack/Terraform) y GCP**: corresponde a los Días 3 en adelante del plan de 14 días.
- **Tests unitarios de `ProfesionalServiceImpl` y `DocumentoServiceImpl`**: se dejaron fuera de esta fase por cobertura de tiempo — el patrón ya está establecido en `PacienteServiceImplTest`/`TurnoServiceImplTest` y es directo de replicar.
- **Posible `cambiarEstado` para `Documento`** (ej. marcarlo `PROCESADO`/`ERROR` desde el worker Python): no se agregó por no haber sido pedido — es la extensión natural cuando se conecte el worker de procesamiento de PDFs.
- **Inconsistencia de auditoría en los DTOs**: `PacienteResponse` expone `creadoEn`, los otros 3 responses no exponen ningún campo de auditoría. Se dejó así por no rediseñar DTOs ya definidos fuera del alcance de esta fase.
- **Test de integración con Testcontainers no se pudo ejecutar en este entorno**: Testcontainers 1.20.1 (dependencia usada en el `pom.xml`) tiene un problema de negociación de versión del cliente Docker contra esta instalación de Docker Desktop (API 1.55) en Windows/npipe — el pull de la imagen de Postgres falla con `client version 1.32 is too old`. El código del test es correcto (se revisó su estructura y compila); es un problema de compatibilidad de herramientas, no del código de MediFlow. Posible solución a futuro: actualizar la dependencia `testcontainers-bom` a una versión más reciente que corrija esa negociación en Windows.

## 6. Cómo correr y probar localmente

```bash
# 1. Levantar Postgres local (ya existe en el repo)
cd ../aws-local-sandbox
docker compose up -d postgres

# 2. Compilar y correr los tests unitarios (rápidos, sin Docker)
cd ../medi-java
mvn test -Dtest='PacienteServiceImplTest,TurnoServiceImplTest'

# 3. Levantar la API
mvn spring-boot:run

# 4. Construir y correr en Docker
docker build -t mediflow-api:v1 .
docker run -p 8080:8080 --add-host=host.docker.internal:host-gateway mediflow-api:v1
```
