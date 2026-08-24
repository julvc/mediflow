data "aws_caller_identity" "actual" {}
data "aws_region" "actual" {}

resource "aws_iam_role" "lambda_ejecucion" {
  name = "mediflow-worker-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Minimo privilegio: cada accion listada explicita, ningun "Action": "*" —
# ver el paso 3.6 del plan original. Cuatro permisos, cuatro razones:
# leer el PDF que dispara el evento, escribir su metadata, mandar el evento
# fallido a la DLQ tras agotar reintentos, y sus propios logs.
resource "aws_iam_role_policy" "lambda_permisos" {
  name = "mediflow-worker-lambda-permisos"
  role = aws_iam_role.lambda_ejecucion.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "LeerDocumentoDeS3"
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${var.bucket_arn}/inbox/*"
      },
      {
        Sid      = "EscribirMetadataEnDynamoDB"
        Effect   = "Allow"
        Action   = "dynamodb:PutItem"
        Resource = var.tabla_arn
      },
      {
        Sid      = "EnviarAColaDLQTrasAgotarReintentos"
        Effect   = "Allow"
        Action   = "sqs:SendMessage"
        Resource = var.dlq_arn
      },
      {
        Sid      = "LeerSecretoDeConfiguracion"
        Effect   = "Allow"
        Action   = "secretsmanager:GetSecretValue"
        Resource = var.secret_arn
      },
      {
        Sid    = "EscribirSusPropiosLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "arn:aws:logs:${data.aws_region.actual.name}:${data.aws_caller_identity.actual.account_id}:log-group:/aws/lambda/${var.nombre_funcion}:*"
      },
    ]
  })
}
