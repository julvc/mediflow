output "tabla_nombre" {
  value = aws_dynamodb_table.documentos.name
}

output "tabla_arn" {
  value = aws_dynamodb_table.documentos.arn
}

output "dlq_arn" {
  value = aws_sqs_queue.dlq.arn
}

output "dlq_url" {
  value = aws_sqs_queue.dlq.url
}

output "secret_arn" {
  value = aws_secretsmanager_secret.worker_config.arn
}
