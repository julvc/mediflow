# MediFlow

Proyecto portfolio que resuelve el mismo dominio de negocio — gestión de turnos médicos y procesamiento de documentos — comparado en dos ejes: **dos nubes distintas** (AWS/GCP) y **dos stacks de backend distintos** (Java/Python), para tener criterio propio y contenido real de entrevista en ambos, no para migrar de uno a otro.

## Qué hace

Un centro médico necesita:
1. Administrar **pacientes, profesionales y turnos** (con reglas reales: un profesional no puede tener dos turnos activos a la misma hora, un turno solo transita entre estados válidos — pendiente → confirmado → completado, o cancelado).
2. Procesar **documentos clínicos** (PDFs) subidos a un storage, validándolos y extrayendo metadata, sin bloquear al usuario mientras se procesan.

## Cómo está resuelto

| Componente | Stack | Rol |
|---|---|---|
| `medi-java` | Spring Boot 3 / Java 17, Postgres, Flyway | API REST completa del dominio (Paciente, Profesional, Turno, Documento), auth JWT + roles — el backend principal |
| `medi-frontend` | React 19, Vite, Tailwind CSS v4 | SPA que consume la API Java: dashboard, calendario de turnos, gestión de pacientes/profesionales/documentos, flujos de registro por rol |
| `medi-python/backend` | FastAPI, SQLAlchemy, Postgres propio | Segunda API REST del mismo dominio (Paciente + auth JWT hoy, como patrón de referencia), en paralelo a Java — no la reemplaza |
| `medi-python/procesador` | pypdf, pymupdf, Pillow | Worker de documentos: agnóstico de nube y usado por ambos backends — biblioteca directa para el backend Python, HTTP (`worker_api.py`) para el backend Java |

**Por qué dos nubes:** la API Java se despliega en **GCP** (Cloud Run + Cloud SQL, el mismo dominio en modelo relacional). El mismo worker Python se valida contra **AWS** (Lambda + DynamoDB single-table, storage por eventos) usando LocalStack para desarrollo y sandboxes gratuitos de AWS Builder Center para validación real — sin gastar un dólar. El valor del proyecto está en decidir *qué cambia y qué no* entre ambas nubes, no en desplegar dos veces lo mismo.

**Por qué dos backends:** mismo razonamiento, otro eje — Java/Spring Boot y Python/FastAPI resolviendo el mismo dominio, con la misma forma de capas (router/controller → service → repository, con interfaces que el service depende para invertir la dependencia). El objetivo es poder defender ambos stacks en una entrevista con código real detrás, no solo teoría.

## Decisiones técnicas que vale la pena mirar

- **Dockerfile multi-stage**: la imagen final corre solo con JRE (Alpine), no con el JDK completo — **353 MB vs. 1.35 GB** de una imagen single-stage equivalente (~74% más chica).
- **Tests de integración con Testcontainers**: contra un Postgres real, no mocks — valida reglas que solo existen en la base (como un índice único parcial de Postgres), aplicando experiencia previa de QA a nivel de arquitectura de tests.
- **Worker Python sin SDKs de nube en su lógica core**: los adaptadores de evento/bucket de cada proveedor envuelven la función, nunca se mezclan con ella — así el mismo código corre en Lambda o en Cloud Run Function sin fork.
- **Costo cero como restricción de diseño**, no solo de presupuesto: cada pieza en AWS corre en LocalStack o en sandboxes con auto-teardown; los recursos de GCP están configurados para escalar a cero cuando no se usan.

## Estado actual

- `medi-java`: dominio completo, API REST, auth JWT + roles, migraciones, Dockerfile y tests unitarios/integración implementados.
- `medi-frontend`: SPA en React completa — auth con refresh de tokens, CRUD de Pacientes/Profesionales, Turnos con vista calendario, Documentos embebidos por turno, registro por rol, tema claro/oscuro.
- `medi-python/procesador`: implementado y probado (valida PDF, extrae metadata, genera thumbnail), con capas SOLID (interfaces + implementaciones inyectadas). Pendiente: adaptadores de evento S3/GCS (Terraform, día 3 en adelante).
- `medi-python/backend`: API completa en paralelo a Java, con la misma profundidad de auth — Paciente, Profesional, Turno (máquina de estados y chequeo de conflicto de horario) y Documento, más roles/RBAC (`requiere_roles()`, reglas de dueño-del-recurso) y rotación de refresh token. 44 tests. Sin auditoría propia todavía (Java sí la tiene).
- Cliente Java (`WorkerClient`, `RestClient` de Spring) que llama a `worker_api.py` — probado con `MockRestServiceServer` y verificado con una llamada real al proceso corriendo. Deliberadamente **no** conectado desde `DocumentoController`: el Javadoc de `DocumentoRequest` dice que el binario nunca pasa por esa API, y hacer que Java lo descargue para reenviarlo al worker violaría esa decisión — su invocador real es la Lambda/Cloud Function disparada por el evento de S3/GCS.
- Infraestructura como código (Terraform AWS/GCP): pendiente.

Ver `medi-java/INFORME-AVANCE.md` para el detalle técnico de lo construido hasta ahora.
