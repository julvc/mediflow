# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

This repo is at the planning stage. `medi-java/` and `medi-python/` are empty
placeholder directories — no application code, build files, or tests exist yet.
There is no build/lint/test tooling to run right now. When code is added to
either directory, update this file with the actual commands (Maven/Gradle
wrapper for `medi-java`, the test runner for `medi-python`, etc.).

`aws-local-sandbox/docker-compose.yml` is the only runnable piece today: it
starts LocalStack (AWS emulator) and a local Postgres 16 container.

```bash
cd aws-local-sandbox
docker compose up -d      # start LocalStack + Postgres
docker compose down       # stop (add -v to also wipe the Postgres volume)
```

## Project intent

MediFlow is a portfolio/learning project comparing the same document-processing
domain deployed on two clouds:

- **`medi-java`** — Spring Boot 3 (Java 17) REST API: `Paciente`, `Profesional`,
  `Turno`, `Documento` domain, Flyway migrations, deployed to GCP Cloud Run.
- **`medi-python`** — a cloud-agnostic worker function
  `procesar_documento(bucket, key)` (validates magic bytes, extracts PDF
  metadata, generates thumbnails) meant to run unmodified as both an AWS
  Lambda and a GCP Cloud Run Function. Keep this function's core logic free
  of any AWS/GCP SDK calls — provider-specific bucket/event adapters should
  wrap it, not be mixed into it, since sharing this code across both clouds
  is the whole point of the design.

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
