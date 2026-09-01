output "bucket_name" {
  value = google_storage_bucket.documentos.name
}

output "bucket_url" {
  value = google_storage_bucket.documentos.url
}
