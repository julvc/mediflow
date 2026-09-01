output "service_url" {
  value = module.compute.service_url
}

output "bucket_name" {
  value = module.storage.bucket_name
}

output "sql_connection_name" {
  value = module.data.sql_connection_name
}

output "function_name" {
  value = module.compute.function_name
}

output "deploy_sa_email" {
  value = module.iam.deploy_sa_email
}

output "wif_provider_name" {
  value = module.iam.wif_provider_name
}
