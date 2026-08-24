variable "project_id" {
  type = string
}

variable "bucket_name" {
  type = string
}

variable "db_password_secret_id" {
  type = string
}

variable "github_repo" {
  type        = string
  description = "owner/repo exacto que puede autenticarse vía WIF (Día 9)"
  default     = "julvc/mediflow"
}

variable "github_rama_deploy" {
  type        = string
  description = "Única rama con permiso de desplegar — un PR de otra rama u otro fork no puede"
  default     = "main"
}
