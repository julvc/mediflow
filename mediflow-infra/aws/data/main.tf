# Single-table design a proposito, no una tabla por entidad: PK "DOC#<key>"
# + SK "META#" hoy; si el worker llegara a procesar pagina por pagina,
# "PAGE#<n>" cabria en la misma partition key sin joins ni tabla nueva.
resource "aws_dynamodb_table" "documentos" {
  name         = "mediflow-documentos"
  billing_mode = "PAY_PER_REQUEST" # sin capacidad provisionada que pagar/gestionar
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }

  attribute {
    name = "SK"
    type = "S"
  }
}

# DLQ del worker: la invocacion asincrona de Lambda (trigger S3) reintenta 2
# veces por defecto sin configuracion extra: si un PDF corrupto sigue
# fallando, el evento cae aca en vez de perderse.
resource "aws_sqs_queue" "dlq" {
  name = "mediflow-worker-dlq"
}

# Provisionado como el lugar donde vivirian secretos reales de config del
# worker (ninguno existe todavia — es la pieza de infraestructura, no un
# secreto en uso). Nunca en .tf/.yml versionado, ver CLAUDE.md.
resource "aws_secretsmanager_secret" "worker_config" {
  name = "mediflow/worker/config"

  # 0 = sin ventana de recuperacion tras destroy: en un sandbox de LocalStack
  # que se recrea seguido, el default de AWS (30 dias, recuperable) bloquea
  # el siguiente apply con "already scheduled for deletion". En un secreto
  # real de produccion, este valor no iria en 0.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "worker_config" {
  secret_id     = aws_secretsmanager_secret.worker_config.id
  secret_string = jsonencode({ placeholder = "sin secretos reales todavia" })
}
