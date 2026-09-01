output "bucket_id" {
  value = module.storage.bucket_id
}

output "tabla_nombre" {
  value = module.data.tabla_nombre
}

output "dlq_url" {
  value = module.data.dlq_url
}

output "function_name" {
  value = module.compute.function_name
}
