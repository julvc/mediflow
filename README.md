# MediFlow

Proyecto portfolio que resuelve el mismo dominio de negocio — gestión de turnos médicos y procesamiento de documentos — desplegado en **dos nubes distintas**, para comparar decisiones de arquitectura en vez de repetir el mismo despliegue dos veces.

## Qué hace

Un centro médico necesita:
1. Administrar **pacientes, profesionales y turnos** (con reglas reales: un profesional no puede tener dos turnos activos a la misma hora, un turno solo transita entre estados válidos — pendiente → confirmado → completado, o cancelado).
2. Procesar **documentos clínicos** (PDFs) subidos a un storage, validándolos y extrayendo metadata, sin bloquear al usuario mientras se procesan.

## Cómo está resuelto

| Componente | Stack | Rol |
|---|---|---|
| `medi-java` | Spring Boot 3 / Java 17, Postgres, Flyway | API REST del dominio (Paciente, Profesional, Turno, Documento), auth JWT + roles |
| `medi-frontend` | React 19, Vite, Tailwind CSS v4 | SPA que consume la API: dashboard, calendario de turnos, gestión de pacientes/profesionales/documentos, flujos de registro por rol |
| `medi-python` | Python 3.12 | Worker `procesar_documento(bucket, key)`: agnóstico de nube a propósito — la misma función corre sin cambios como AWS Lambda o como GCP Cloud Run Function |

**Por qué dos nubes:** la API Java se despliega en **GCP** (Cloud Run + Cloud SQL, el mismo dominio en modelo relacional). El mismo worker Python se valida contra **AWS** (Lambda + DynamoDB single-table, storage por eventos) usando LocalStack para desarrollo y sandboxes gratuitos de AWS Builder Center para validación real — sin gastar un dólar. El valor del proyecto está en decidir *qué cambia y qué no* entre ambas nubes, no en desplegar dos veces lo mismo.

## Decisiones técnicas que vale la pena mirar

- **Dockerfile multi-stage**: la imagen final corre solo con JRE (Alpine), no con el JDK completo — **353 MB vs. 1.35 GB** de una imagen single-stage equivalente (~74% más chica).
- **Tests de integración con Testcontainers**: contra un Postgres real, no mocks — valida reglas que solo existen en la base (como un índice único parcial de Postgres), aplicando experiencia previa de QA a nivel de arquitectura de tests.
- **Worker Python sin SDKs de nube en su lógica core**: los adaptadores de evento/bucket de cada proveedor envuelven la función, nunca se mezclan con ella — así el mismo código corre en Lambda o en Cloud Run Function sin fork.
- **Costo cero como restricción de diseño**, no solo de presupuesto: cada pieza en AWS corre en LocalStack o en sandboxes con auto-teardown; los recursos de GCP están configurados para escalar a cero cuando no se usan.

## Estado actual

- `medi-java`: dominio completo, API REST, auth JWT + roles, migraciones, Dockerfile y tests unitarios/integración implementados.
- `medi-frontend`: SPA en React completa — auth con refresh de tokens, CRUD de Pacientes/Profesionales, Turnos con vista calendario, Documentos embebidos por turno, registro por rol, tema claro/oscuro.
- `medi-python`: pendiente.
- Infraestructura como código (Terraform AWS/GCP): pendiente.

Ver `medi-java/INFORME-AVANCE.md` para el detalle técnico de lo construido hasta ahora.
