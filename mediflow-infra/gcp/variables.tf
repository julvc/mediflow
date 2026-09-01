variable "project_id" {
  type        = string
  description = "Proyecto GCP (Día 5 del plan: mediflow-lab)"

  validation {
    condition     = length(var.project_id) > 0
    error_message = "project_id no puede estar vacío."
  }
}

variable "region" {
  type    = string
  default = "us-central1" # donde aplica el Always Free de Cloud Run

  validation {
    condition     = can(regex("^[a-z]+-[a-z]+[0-9]$", var.region))
    error_message = "region debe tener el formato de una región GCP, ej: us-central1."
  }
}

variable "imagen_api" {
  type        = string
  description = "Imagen de mediflow-api en Artifact Registry. Placeholder hasta el primer build/push (Día 5)."
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}
