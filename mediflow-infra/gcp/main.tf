# Día 5, paso 2: sin esto ningún recurso de abajo se puede crear — GCP
# exige la API habilitada antes de usar el servicio. disable_on_destroy =
# false a propósito: un `destroy` de este sandbox no debe desactivar APIs
# que otro proyecto/persona pudiera compartir, y reactivarlas tiene demora
# de propagación que solo complica el próximo apply.
locals {
  apis = [
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "sqladmin.googleapis.com",
    "secretmanager.googleapis.com",
    "eventarc.googleapis.com",
    "cloudbuild.googleapis.com",
    "cloudfunctions.googleapis.com",
    "servicenetworking.googleapis.com",
    "iamcredentials.googleapis.com", # requerido por Workload Identity Federation
    "compute.googleapis.com",        # VPC + peering privado de Cloud SQL
    "monitoring.googleapis.com",
    "logging.googleapis.com",
    "cloudtrace.googleapis.com",
  ]
}

resource "google_project_service" "apis" {
  for_each           = toset(local.apis)
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

module "storage" {
  source     = "./storage"
  project_id = var.project_id
  region     = var.region
  depends_on = [google_project_service.apis]
}

module "data" {
  source     = "./data"
  region     = var.region
  depends_on = [google_project_service.apis]
}

module "iam" {
  source                = "./iam"
  project_id            = var.project_id
  bucket_name           = module.storage.bucket_name
  db_password_secret_id = module.data.db_password_secret_id
  depends_on            = [google_project_service.apis]
}

module "compute" {
  source                = "./compute"
  project_id            = var.project_id
  region                = var.region
  imagen_api            = var.imagen_api
  api_sa_email          = module.iam.api_sa_email
  worker_sa_email       = module.iam.worker_sa_email
  sql_connection_name   = module.data.sql_connection_name
  sql_private_ip        = module.data.sql_private_ip
  vpc_connector_id      = module.data.vpc_connector_id
  db_password_secret_id = module.data.db_password_secret_id
  network_id            = module.data.network_id
  subnetwork_id         = module.data.subnetwork_id
  bucket_name           = module.storage.bucket_name
}

# Día 10 — alerta si la tasa de error del servicio supera 1%. El dashboard
# completo (p95, instancias activas) queda para después del primer deploy
# real: diseñar gráficos sin métricas reales que mirar es trabajo
# especulativo, la alerta sí tiene un umbral concreto y accionable ahora.
resource "google_monitoring_alert_policy" "error_rate" {
  display_name = "mediflow-api: tasa de error > 1%"
  combiner     = "OR"

  conditions {
    display_name = "request_count con response_code_class=5xx"
    condition_threshold {
      filter          = "resource.type = \"cloud_run_revision\" AND resource.labels.service_name = \"mediflow-api\" AND metric.type = \"run.googleapis.com/request_count\" AND metric.labels.response_code_class = \"5xx\""
      comparison      = "COMPARISON_GT"
      threshold_value = 0.01
      duration        = "60s"
      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  notification_channels = [] # sin canal configurado todavia — agregar un email/Slack cuando exista

  depends_on = [google_project_service.apis]
}
