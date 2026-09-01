output "function_arn" {
  value = aws_lambda_function.worker.arn
}

output "function_name" {
  value = aws_lambda_function.worker.function_name
}

output "invoke_arn" {
  value = aws_lambda_function.worker.invoke_arn
}
