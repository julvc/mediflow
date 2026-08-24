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
  `mediflow-infra/aws/` and `mediflow-infra/gcp/` (not yet created) —
  `storage/`, `compute/`, `data/`, `iam/`.

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
