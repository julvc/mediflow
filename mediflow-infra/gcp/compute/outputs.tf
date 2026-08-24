output "service_url" {
  value = google_cloud_run_v2_service.api.uri
}

output "function_name" {
  value = google_cloudfunctions2_function.worker.name
}
