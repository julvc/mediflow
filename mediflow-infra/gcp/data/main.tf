# Red propia y angosta, solo para el peering privado de Cloud SQL — nada de
# subredes ni firewalls especulativos. Cloud Run se conecta por egress VPC
# directo (sin conector aparte), Día 6 del plan.
resource "google_compute_network" "privada" {
  name                    = "mediflow-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "privada" {
  name          = "mediflow-subnet"
  ip_cidr_range = "10.10.0.0/24"
  region        = var.region
  network       = google_compute_network.privada.id
}

# Rango reservado para el peering de Service Networking (Cloud SQL vive en
# la red de Google, no en la nuestra — este rango es el "puente" entre ambas).
resource "google_compute_global_address" "rango_privado" {
  name          = "mediflow-sql-rango-privado"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.privada.id
}

resource "google_service_networking_connection" "peering_sql" {
  network                 = google_compute_network.privada.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.rango_privado.name]
}

# El worker (Cloud Run Function gen2) no soporta el mismo mount de socket
# Unix en /cloudsql que Cloud Run v2 sí tiene — necesita un conector de VPC
# clásico para poder alcanzar la IP privada de Cloud SQL por TCP. Rango /28
# dedicado y separado de mediflow-subnet: un conector no puede compartir
# CIDR con otra subred.
resource "google_vpc_access_connector" "worker" {
  name          = "mediflow-worker-conn"
  region        = var.region
  network       = google_compute_network.privada.name
  ip_cidr_range = "10.10.1.0/28"
}

# db-f1-micro, single-zone, sin backups, sin IP pública — exactamente lo que
# pide el Día 6: cubierto por el Always Free trial, cero alta disponibilidad
# porque este es un sandbox de aprendizaje, no producción.
resource "google_sql_database_instance" "principal" {
  name             = "mediflow-db"
  database_version = "POSTGRES_16"
  region           = var.region

  settings {
    tier              = "db-f1-micro"
    availability_type = "ZONAL"
    disk_size         = 10
    backup_configuration {
      enabled = false
    }
    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.privada.id
    }
  }

  deletion_protection = false # sandbox: se destruye/recrea seguido

  depends_on = [google_service_networking_connection.peering_sql]
}

resource "google_sql_database" "mediflow" {
  name     = "mediflow"
  instance = google_sql_database_instance.principal.name
}

resource "google_sql_user" "app" {
  name     = "mediflow_app"
  instance = google_sql_database_instance.principal.name
  password = random_password.db.result
}

resource "random_password" "db" {
  length  = 24
  special = false # el conector de Cloud Run pasa esto por variable de entorno; sin caracteres que compliquen el escape
}

# Espejo de aws_secretsmanager_secret (mediflow-infra/aws/data/main.tf):
# la password nunca va en .tf ni en el estado en texto plano fuera de aquí.
resource "google_secret_manager_secret" "db_password" {
  secret_id = "mediflow-db-password"

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db.result
}
