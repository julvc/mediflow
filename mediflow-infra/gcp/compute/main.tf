resource "google_artifact_registry_repository" "api" {
  repository_id = "mediflow-api"
  location      = var.region
  format        = "DOCKER"
}

# lifecycle.ignore_changes en la imagen: Terraform aprovisiona el servicio,
# pero el Día 9 (CI/CD) es quien despliega imágenes nuevas vía
# `gcloud run deploy --image ...`. Sin esto, cada `apply` pisaría la imagen
# real con el placeholder de var.imagen_api.
resource "google_cloud_run_v2_service" "api" {
  name     = "mediflow-api"
  location = var.region

  template {
    service_account = var.api_sa_email

    containers {
      image = var.imagen_api

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "cloud" # logback-spring.xml + application-cloud.yml: JSON logging y datasource de Cloud SQL
      }
      env {
        name  = "DB_CONNECTION_NAME"
        value = var.sql_connection_name
      }
      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = var.db_password_secret_id
            version = "latest"
          }
        }
      }
    }

    # Sin volume/volumes de cloud_sql_instance a propósito: application-cloud.yml
    # usa el Cloud SQL JDBC Socket Factory (dependencia en pom.xml), que abre su
    # propio túnel autenticado por IAM en vez de depender del socket Unix que
    # Cloud Run monta con ese mecanismo. Lo que SÍ sigue haciendo falta es esta
    # ruta de red: la instancia no tiene IP pública (Día 6), así que el
    # contenedor necesita salir por la misma VPC para poder alcanzar su IP
    # privada — sin esto, el socket factory no tiene cómo llegar a la instancia.
    vpc_access {
      network_interfaces {
        network    = var.network_id
        subnetwork = var.subnetwork_id
      }
      egress = "PRIVATE_RANGES_ONLY"
    }

    scaling {
      min_instance_count = 0
      max_instance_count = 3 # freno de mano — nunca escalado infinito
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }
}

resource "google_cloud_run_v2_service_iam_member" "api_publico" {
  name     = google_cloud_run_v2_service.api.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers" # --allow-unauthenticated del Día 5
}

# Empaqueta compute/function/ (adaptador del evento GCS, analogo a
# aws/compute/lambda/handler.py) + procesador/ copiado ahí por build.sh —
# mismo patron que el lado AWS: Terraform solo empaqueta, no corre pip.
data "archive_file" "function_zip" {
  type        = "zip"
  source_dir  = "${path.module}/function"
  output_path = "${path.module}/build/function.zip"
}

resource "google_storage_bucket" "function_source" {
  name                        = "${var.project_id}-mediflow-function-source"
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = true
}

resource "google_storage_bucket_object" "function_zip" {
  name   = "function-${data.archive_file.function_zip.output_md5}.zip"
  bucket = google_storage_bucket.function_source.name
  source = data.archive_file.function_zip.output_path
}

resource "google_cloudfunctions2_function" "worker" {
  name     = "mediflow-worker"
  location = var.region

  build_config {
    runtime     = "python312"
    entry_point = "procesar_documento"
    source {
      storage_source {
        bucket = google_storage_bucket.function_source.name
        object = google_storage_bucket_object.function_zip.name
      }
    }
  }

  service_config {
    max_instance_count    = 3
    available_memory      = "512M"
    timeout_seconds       = 60
    service_account_email = var.worker_sa_email

    # ponytail: campo con mayor riesgo de haber cambiado de nombre entre
    # versiones del provider — verificar contra `terraform plan` real antes
    # de confiar en que aplica tal cual. Necesario porque la instancia de
    # Cloud SQL no tiene IP pública (Día 6): sin una ruta de red hacia la
    # VPC, main.py no tiene cómo abrir el socket Unix en /cloudsql/....
    vpc_connector                 = var.vpc_connector_id
    vpc_connector_egress_settings = "PRIVATE_RANGES_ONLY"

    environment_variables = {
      DB_HOST = var.sql_private_ip
    }

    secret_environment_variables {
      key        = "DB_PASSWORD"
      secret     = var.db_password_secret_id
      version    = "latest"
      project_id = var.project_id
    }
  }

  event_trigger {
    trigger_region        = var.region
    event_type            = "google.cloud.storage.object.v1.finalized"
    service_account_email = var.worker_sa_email
    event_filters {
      attribute = "bucket"
      value     = var.bucket_name
    }
  }
}
