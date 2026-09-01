# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

- **`medi-java/`** — complete Spring Boot 3 (Java 17) REST API: `Paciente`,
  `Profesional`, `Turno`, `Documento` domain, Flyway migrations, JWT auth +
  role-based `@PreAuthorize`/`@PostAuthorize` (roles `PACIENTE`/`PROFESIONAL`/
  `ADMIN`), unit + integration tests (Testcontainers).
  ```bash
  cd medi-java
  mvn compile              # or mvn -q compile
  mvn test                 # full suite, needs Docker (Testcontainers)
  mvn spring-boot:run      # http://localhost:8080/api/v1, needs Postgres up first
  ```
- **`medi-frontend/`** — React 19 + Vite SPA consuming that API. Tailwind CSS v4
  (`@tailwindcss/vite`, no postcss config), `react-router-dom`, `lucide-react`
  as the only discretionary dependency. See `medi-frontend/README.md`-equivalent
  context below and the CSS tokens in `medi-frontend/src/index.css` for the
  design system (light default, dark via `[data-theme="dark"]` or
  `prefers-color-scheme`).
  ```bash
  cd medi-frontend
  npm install
  npm run dev               # http://localhost:5173, --strictPort recommended
                             # to avoid drifting off the port the backend's CORS allows
  npm run build              # production build, what a recruiter would see compiled
  ```
- **`medi-python/`** has two independent pieces, both SOLID-layered on purpose
  (interfaces the orchestrator/service depends on, concrete implementations
  injected — see `.superpowers/`-free comments in the code itself for the
  "why" of each split):
  - **`procesador/`** — the cloud-agnostic worker core.
    `ProcesadorDocumentoService.procesar(contenido: bytes, nombre_archivo: str)`
    validates PDF magic bytes, extracts metadata (`ExtractorMetadataPyPDF`,
    wraps `pypdf`), and rasterizes the first page into a PNG thumbnail
    (`GeneradorThumbnailPyMuPDF` — `pymupdf` renders the pixels, since Pillow
    alone cannot read PDF pages; Pillow does the resize/encode). Both are
    injected via `Protocol` interfaces (DIP), so swapping the renderer later
    is a new implementation class, not a change to the orchestrator. Raises
    `DocumentoInvalidoError` instead of a sentinel value, so a future
    Lambda/Cloud Run Function adapter can just let it propagate into that
    platform's native retry/DLQ mechanism. No AWS/GCP SDK, no FastAPI import
    here — the `(bucket, key)` event adapters are Día 3/7 work, not yet written.
  - **`worker_api.py`** — a thin FastAPI wrapper (`POST /procesar`, multipart
    upload) around `procesador/`. This is the integration point for
    **medi-java**, which can't import Python directly and calls this over
    HTTP (`com.mediflow.api.worker.WorkerClient`, a Spring `RestClient` —
    tested with `MockRestServiceServer` and curl-verified against the real
    running process); **medi-python/backend** instead imports `procesador/`
    as a library (no HTTP hop to itself). `WorkerClient` is intentionally
    **not** wired into `DocumentoController` — `DocumentoRequest`'s own
    Javadoc says the binary never passes through that API (only its
    `urlStorage` reference does); having Java download the file to forward
    it to the worker would violate that. The client stays as a tested,
    working proof the HTTP contract works — its real caller, in the eventual
    cloud architecture, is the Lambda/Cloud Run Function triggered directly
    by the S3/GCS event, not the Java API.
  - **`backend/`** — a second, independent REST API (FastAPI + SQLAlchemy +
    JWT), built **in parallel to `medi-java`, not replacing it** — the point
    is comparing the same domain in both stacks, same as the AWS/GCP split
    compares clouds. Same layering as medi-java (router → service → repository,
    interfaces the service depends on injected at the router), now with the
    same auth depth: `Paciente`, `Profesional`, `Turno` (state machine +
    same-slot conflict check as medi-java), `Documento`, `/auth/registro`
    (same domain-vinculo validation and anti-IDOR email check as
    `AuthServiceImpl`) + `/auth/login` + `/auth/refresh` (rotation, SHA-256
    hash, same mechanism as Java's `RefreshToken`) + `/auth/logout`, and
    role-based access (`requiere_roles()` — a dependency factory, the
    closest FastAPI equivalent to `@PreAuthorize`; owner-of-resource checks
    like Java's `@recursoAuth` are explicit code in the router/service, same
    as Java). Seeds `admin@mediflow.cl` / `admin1234` on startup — same
    credentials as Java's Flyway seed, since public registro can't create the
    first ADMIN. Runs against its **own** Postgres instance
    (`aws-local-sandbox` service `postgres-python`, port 5433, db
    `mediflow_python`) — deliberately not sharing schema with medi-java's
    Flyway-managed `mediflow` database.
  ```bash
  cd medi-python
  python -m venv .venv && .venv/Scripts/pip install -r requirements.txt   # Windows
  .venv/Scripts/python -m pytest -v                                       # all tests (SQLite, no Docker needed)
  .venv/Scripts/python -m uvicorn backend.main:app --port 8001            # needs postgres-python up
  .venv/Scripts/python -m uvicorn worker_api:app --port 8000
  ```

`aws-local-sandbox/docker-compose.yml` starts LocalStack (AWS emulator) and the
local Postgres 16 container the backend needs:

```bash
cd aws-local-sandbox
docker compose up -d postgres   # only Postgres is needed for medi-java/medi-frontend work
docker compose down             # stop (add -v to also wipe the Postgres volume)
```

Seed admin account (from `V2__auth_schema.sql`): `admin@mediflow.cl` / `admin1234`.
CORS on the backend defaults to `http://localhost:5173,http://localhost:3000`
(`mediflow.cors.allowed-origins` in `application.yml`, overridable via
`CORS_ALLOWED_ORIGINS`) — if the frontend dev server drifts to another port
(e.g. because 5173 was already taken), requests will fail client-side with a
generic "Failed to fetch" until either the origin is added or the port frees up.

## Project intent

MediFlow is a portfolio/learning project comparing the same domain across two
axes: two clouds (AWS/GCP), and — as of this session — two backend stacks
(Java/Python), specifically so the author can compare and speak to both in
interviews. Neither comparison is a migration; both sides of each pair stay
alive and are meant to be read side by side.

- **`medi-java`** — Spring Boot 3 (Java 17) REST API: `Paciente`, `Profesional`,
  `Turno`, `Documento` domain, Flyway migrations, deployed to GCP Cloud Run.
  The primary/most complete backend (full JWT+refresh+RBAC, all 4 domains, 35
  tests).
- **`medi-python/backend`** — a second REST API (FastAPI + SQLAlchemy), same
  layered/SOLID shape as medi-java, covering `Paciente` + basic JWT auth today
  as the reference pattern for extending to the rest of the domain. Runs
  against its own Postgres, not medi-java's.
- **`medi-python/procesador`** — a cloud-agnostic worker function
  (`ProcesadorDocumentoService.procesar`, validates magic bytes, extracts PDF
  metadata, generates thumbnails) meant to run unmodified as both an AWS
  Lambda and a GCP Cloud Run Function, AND to be callable from both backends
  (imported directly by `medi-python/backend`, reached over HTTP via
  `worker_api.py` by `medi-java`). Keep this function's core logic free of any
  AWS/GCP SDK calls (and free of FastAPI) — adapters wrap it, never mix into
  it, since sharing this code across both clouds *and* both backends is the
  whole point of the design.

### Cloud split (see `plan-14-dias-aws-gcp-v2-costo-cero.md`)

- **AWS side** is developed against **LocalStack** (via `tflocal`/`awslocal`)
  and only validated against real AWS inside short-lived, no-cost **AWS
  Builder Center sandboxes** (8h/week, auto-teardown). Storage there uses
  **DynamoDB single-table design** (PK/SK: `DOC#<id>`/`META#`, `DOC#<id>`/`PAGE#<n>`),
  not RDS.
- **GCP side** is the permanent, publicly reachable deployment: Cloud Run
  (API) + Cloud SQL (Postgres 16, relational version of the same domain) +
  Cloud Storage + Eventarc-triggered Cloud Run Function (worker).
- Terraform modules are meant to mirror each other under
  `mediflow-infra/aws/` and `mediflow-infra/gcp/` — `storage/`, `compute/`,
  `data/`, `iam/`. **AWS side is done** (Día 3): 13 recursos aplicados vía
  `tflocal` contra LocalStack, verificados end-to-end (subida de PDF real a
  `s3://mediflow-documentos/inbox/` → Lambda → metadata+thumbnail en
  DynamoDB; archivo inválido correctamente rechazado con
  `DocumentoInvalidoError`). **GCP side is written and `terraform validate`
  passes (Días 8-10)**, apply pending `gcloud auth login` (SDK installed via
  winget, project `mediflow-lab`) — step-by-step from credentials to a
  working deploy, with the concepts explained, in
  `mediflow-infra/gcp/GUIA-EJECUCION.md`:
  - `storage/`: un solo `google_storage_bucket` cubre lo que en AWS son 3
    recursos (`uniform_bucket_level_access` + `public_access_prevention` +
    `lifecycle_rule` en vez de versioning/lifecycle/public-access-block
    separados) — diferencia real de forma entre providers.
  - `data/`: VPC propia + peering de Service Networking +
    `google_sql_database_instance` (Postgres 16, `db-f1-micro`, sin IP
    pública) — a diferencia de DynamoDB en AWS, Cloud SQL exige construir el
    puente de red antes de poder existir sin IP pública.
  - `compute/`: Artifact Registry + `google_cloud_run_v2_service` (con
    `lifecycle.ignore_changes` en la imagen del contenedor, porque CI/CD
    despliega imágenes nuevas vía `gcloud run deploy` después de que
    Terraform crea el servicio — sin el ignore_changes, cada apply pisaría
    la imagen real con el placeholder) + `google_cloudfunctions2_function`
    gen2 (worker, Eventarc trigger) con su propio adaptador
    (`compute/function/main.py`, análogo a `aws/compute/lambda/handler.py`
    pero escribiendo a Cloud SQL/Postgres en vez de DynamoDB — mismo
    `procesador/` sin cambios, solo el adaptador sabe en qué nube corre).
    `compute/build.sh` solo copia `procesador/` (sin vendorizar wheels como
    en AWS): Cloud Functions gen2 corre `pip install` del lado del servidor
    vía buildpacks, Lambda no.
  - **Conectividad a Cloud SQL, dos mecanismos distintos, a propósito**:
    `mediflow-api` (Cloud Run v2 + Java) usa el **Cloud SQL JDBC Socket
    Factory** (`com.google.cloud.sql:postgres-socket-factory` en
    `medi-java/pom.xml` + `application-cloud.yml`) en vez del mount nativo
    `/cloudsql` de Cloud Run — la primera versión de este Terraform montaba
    ese volumen, pero `medi-java` no tenía nada que supiera leerlo (un JDBC
    estándar no habla socket Unix sin ayuda); se sacó el volumen y se agregó
    la dependencia real. `mediflow-worker` (Cloud Functions gen2 + Python)
    no tiene ese mount disponible en absoluto, así que se conecta por TCP a
    la IP privada de la instancia (`DB_HOST`) a través de un
    `google_vpc_access_connector` clásico — campo marcado con `ponytail:` en
    `compute/main.tf` por ser el de mayor riesgo de haber cambiado de
    nombre en el provider, verificar contra el primer `terraform plan` real.
  - `iam/`: dos service accounts de runtime (API, worker) con mínimo
    privilegio, más (Día 9) un `google_iam_workload_identity_pool` +
    provider OIDC y una `mediflow-deploy-sa` para CI/CD — cero JSON de
    service account. El trust por repo+rama vive en el
    `attribute_condition` del provider (lado GCP), no en el YAML del
    workflow (`.github/workflows/ci.yml`, `deploy.yml`) — el YAML es
    comodidad, la condición es la frontera de seguridad real.
  - Backend de state en GCS queda comentado en `versions.tf` hasta el
    bootstrap manual (`gsutil mb gs://mediflow-lab-tfstate`) — problema
    clásico de huevo-y-gallina, el bucket no puede crearlo el mismo config
    que lo va a usar como backend.
  - Root `main.tf` habilita las 13 APIs necesarias (`google_project_service`,
    `disable_on_destroy = false`) antes de que cualquier módulo intente usar
    el servicio — GCP exige la API habilitada primero, a diferencia de AWS
    donde los servicios ya están disponibles por defecto en la cuenta.
  - **Día 10**: `medi-java` tiene `logback-spring.xml` con perfil `cloud`
    (JSON vía `logstash-logback-encoder`, campo `severity` en vez de
    `level` porque es el nombre que Cloud Logging espera para colorear por
    nivel) — el Cloud Run service lo activa con
    `SPRING_PROFILES_ACTIVE=cloud`; local/tests siguen con el patrón de
    consola normal. `google_monitoring_alert_policy` (tasa de error > 1%)
    ya está en el root; el dashboard completo (p95, instancias) se diseña
    después del primer deploy real — hacerlo antes es adivinar sobre
    métricas que no existen todavía.

  ```bash
  cd mediflow-infra/aws
  tflocal init
  tflocal apply -auto-approve   # LocalStack debe estar arriba (aws-local-sandbox)
  ```

  Dos gotchas de entorno encontrados y resueltos, documentados por si
  reaparecen:
  - **Avast intercepta el handshake mTLS interno de Terraform** (core ↔
    subprocesos `terraform-provider-*.exe`, tráfico 100% loopback) vía su
    escaneo HTTPS de Web Shield, causando
    `x509: certificate signed by unknown authority` en `init`/cualquier
    plan/apply. No es un problema de Terraform ni de LocalStack. Workaround
    verificado: desactivar temporalmente los escudos de Avast (icono de
    bandeja → Control de escudos → deshabilitar N minutos) solo mientras se
    corre el comando — nunca cambiar la config de Avast de forma
    automatizada, eso lo hace el usuario.
  - **`aws_s3_bucket_lifecycle_configuration` nunca confirma su propio
    create contra LocalStack Community**: el *waiter* del provider AWS v5
    espera un campo (`transition_default_minimum_object_size`) que
    LocalStack no persiste/devuelve, y siempre hace timeout a los 3 min —
    aunque el recurso sí se crea correctamente del lado de LocalStack.
    `storage/main.tf` tiene `lifecycle { ignore_changes =
    [transition_default_minimum_object_size] }` para que **updates**
    posteriores no vuelvan a chocar con esto, pero el waiter igual se activa
    en cualquier **create** desde cero (p.ej. tras un `destroy`) — el
    procedimiento en ese caso es dejar que falle y reconciliar con
    `tflocal import module.storage.aws_s3_bucket_lifecycle_configuration.documentos
    mediflow-documentos`, no reintentar el apply esperando que cambie.
  - **`aws_secretsmanager_secret` con nombre fijo falla al recrearse tras un
    `destroy`**: AWS deja el secreto en estado "programado para eliminación"
    por una ventana de recuperación (comportamiento real de AWS, no de
    LocalStack) y el siguiente `apply` choca con
    `already scheduled for deletion`. Se agregó `recovery_window_in_days = 0`
    al recurso (`data/main.tf`) — correcto para secretos placeholder en un
    sandbox que se destruye/recrea seguido; nunca en un secreto real de
    producción.
  - LocalStack Community **sí** simula los reintentos automáticos de
    invocación asíncrona de Lambda (S3 trigger) y la entrega a la DLQ tras
    agotarlos — solo que puede tardar bastante más que los ~3 reintentos
    inmediatos de AWS real (se confirmó con un archivo inválido: llegó a la
    DLQ, pero después de varios minutos de espera, no al toque). No asumir
    que está vacía tras una espera corta.
  - **Chequeo de idempotencia confirmado**: `tflocal destroy -auto-approve`
    seguido de `tflocal apply -auto-approve` (+ el import puntual de
    lifecycle_configuration de arriba) reconstruye los 13 recursos y
    `tflocal plan` queda en "No changes". Si el bucket tiene objetos subidos
    fuera de Terraform (pruebas manuales), `destroy` falla con
    `BucketNotEmpty` por ser versionado — hay que vaciar las versiones con
    `awslocal s3api list-object-versions` + `delete-object --version-id`
    antes de reintentar (no es un bug, es el comportamiento correcto de
    seguridad de S3 versionado).

### Constraints that shape any infra/code suggestions

- **Zero budget is a hard constraint.** Every AWS step must run in LocalStack,
  a Builder Sandbox, or an always-free tier — never against a paid resource.
  If a suggested approach isn't free, say so explicitly rather than assuming.
- GCP resources (Cloud SQL especially) should default to teardown-friendly
  settings (`--min-instances 0`, activation policy `NEVER` when idle,
  `--max-instances` always set) since they run against trial credit.
- Secrets belong in Secrets Manager / Secret Manager — never in `.tf`, `.yml`,
  or committed `.env` files. (Note: `aws-local-sandbox/docker-compose.yml`
  currently has a `LOCALSTACK_AUTH_TOKEN` hardcoded — flag this if touching
  that file.)
- `plan-14-dias-aws-gcp-v2-costo-cero.md` and `anexo-a-docker-kubernetes.md`
  are gitignored (personal planning docs, not meant to be published) — don't
  assume they're part of the public-facing portfolio content.

# Idioma y Estilo

- Comunícate siempre en español neutral latinoamericano.
- Usa estrictamente el pronombre "tú" y las formas de tuteo.
- Está prohibido el uso del voseo y modismos regionales argentinos.
