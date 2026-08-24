resource "google_service_account" "api" {
  account_id   = "mediflow-api-sa"
  display_name = "mediflow-api (Cloud Run)"
}

# 3 roles, 3 razones: conectarse a Cloud SQL por el conector integrado, leer
# la password del secreto, y firmar Signed URLs para que el binario nunca
# pase por la API (Día 7 — mismo principio que WorkerClient en medi-java).
resource "google_project_iam_member" "api_cloudsql" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.api.email}"
}

resource "google_secret_manager_secret_iam_member" "api_lee_db_password" {
  secret_id = var.db_password_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}

resource "google_storage_bucket_iam_member" "api_firma_urls" {
  bucket = var.bucket_name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.api.email}"
}

resource "google_service_account_iam_member" "api_firma_su_propio_token" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.api.email}"
}

resource "google_service_account" "worker" {
  account_id   = "mediflow-worker-sa"
  display_name = "mediflow-worker (Cloud Run Function)"
}

# El worker solo necesita leer el objeto que dispara el evento — nada de
# permisos de escritura en el bucket ni acceso a Cloud SQL.
resource "google_storage_bucket_iam_member" "worker_lee_documentos" {
  bucket = var.bucket_name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.worker.email}"
}

# El trigger de Eventarc invoca la function usando esta SA — necesita poder
# recibir el evento y poder invocar el servicio Cloud Run subyacente del
# gen2 function (equivalente GCP del aws_lambda_permission de S3→Lambda).
resource "google_project_iam_member" "worker_recibe_eventos" {
  project = var.project_id
  role    = "roles/eventarc.eventReceiver"
  member  = "serviceAccount:${google_service_account.worker.email}"
}

resource "google_project_iam_member" "worker_invoca_run" {
  project = var.project_id
  role    = "roles/run.invoker"
  member  = "serviceAccount:${google_service_account.worker.email}"
}

# Día 9 — CI/CD sin credenciales estáticas. GitHub Actions cambia su propio
# OIDC token (emitido por GitHub, nadie lo guarda) por un token de GCP de
# corta duración. Cero JSON de service account descargado o guardado como
# secret — el error de seguridad #1 en GCP que el plan pide evitar.
resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github-actions"
  display_name              = "GitHub Actions"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"
  display_name                       = "GitHub"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # El trust real vive acá, no en el workflow YAML: sin esta condición,
  # CUALQUIER repo de GitHub que conozca el nombre del pool podría pedir un
  # token. Restringido por repo Y por rama — un PR de un fork no puede
  # desplegar, solo un push a refs/heads/main del repo real.
  attribute_condition = "assertion.repository == '${var.github_repo}' && assertion.ref == 'refs/heads/${var.github_rama_deploy}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account" "deploy" {
  account_id   = "mediflow-deploy-sa"
  display_name = "mediflow CI/CD (GitHub Actions)"
}

# Únicos 3 permisos que el pipeline necesita: subir la imagen, desplegar el
# servicio, y poder "actuar como" la SA de runtime al desplegar (GCP exige
# esto explícito — de otro modo cualquier SA podría atarle su identidad a
# un servicio sin haberlo pedido).
resource "google_project_iam_member" "deploy_push_imagenes" {
  project = var.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_project_iam_member" "deploy_despliega_run" {
  project = var.project_id
  role    = "roles/run.developer"
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_service_account_iam_member" "deploy_actua_como_api" {
  service_account_id = google_service_account.api.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deploy.email}"
}

# Solo identidades que llegan por ESTE pool, con el atributo repository
# exacto, pueden hacerse pasar por mediflow-deploy-sa — el amarre final
# entre el WIF de arriba y esta service account.
resource "google_service_account_iam_member" "deploy_via_wif" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repo}"
}
