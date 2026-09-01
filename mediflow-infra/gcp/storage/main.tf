# En AWS, versioning + lifecycle + public-access-block son tres recursos
# separados (storage/main.tf en mediflow-infra/aws). En GCP los tres caben en
# los atributos de un solo google_storage_bucket — diferencia real de forma
# entre providers, no una simplificación nuestra.
resource "google_storage_bucket" "documentos" {
  name                        = "${var.project_id}-mediflow-docs"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = true # sandbox de aprendizaje, no producción

  lifecycle_rule {
    condition {
      age = 7
    }
    action {
      type = "Delete"
    }
  }
}
