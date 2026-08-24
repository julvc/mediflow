output "sql_connection_name" {
  value = google_sql_database_instance.principal.connection_name
}

output "sql_private_ip" {
  value = google_sql_database_instance.principal.private_ip_address
}

output "vpc_connector_id" {
  value = google_vpc_access_connector.worker.id
}

output "sql_instance_name" {
  value = google_sql_database_instance.principal.name
}

output "db_password_secret_id" {
  value = google_secret_manager_secret.db_password.secret_id
}

output "network_id" {
  value = google_compute_network.privada.id
}

output "subnetwork_id" {
  value = google_compute_subnetwork.privada.id
}
