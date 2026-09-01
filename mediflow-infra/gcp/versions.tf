terraform {
  required_version = ">= 1.5"

  # Backend GCS (locking nativo, Día 8) — comentado hasta el bootstrap: el
  # bucket de state no puede crearlo este mismo config (problema clásico de
  # huevo-y-gallina). Una vez autenticado gcloud:
  #   gsutil mb -l us-central1 gs://mediflow-lab-tfstate
  #   gsutil versioning set on gs://mediflow-lab-tfstate
  # y recién ahí descomentar esto y correr `terraform init -migrate-state`.
  # backend "gcs" {
  #   bucket = "mediflow-lab-tfstate"
  #   prefix = "gcp"
  # }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
