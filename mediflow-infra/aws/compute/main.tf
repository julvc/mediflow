# El contenido de lambda/ se arma con build.sh (copia procesador/ desde
# medi-python sin modificarlo + instala pypdf/pymupdf/Pillow como wheels
# manylinux, para Lambda-Linux aunque se compile desde Windows). Terraform
# solo empaqueta lo que ya esta ahi — no corre pip.
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda"
  output_path = "${path.module}/build/lambda.zip"
}

resource "aws_lambda_function" "worker" {
  function_name = var.nombre_funcion
  role          = var.role_arn
  handler       = "handler.procesar_documento"
  runtime       = "python3.12"
  timeout       = 30
  memory_size   = 512

  filename         = data.archive_file.lambda_zip.output_path
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256

  environment {
    variables = {
      TABLA_DOCUMENTOS = var.tabla_nombre
      SECRETO_ARN      = var.secret_arn
    }
  }

  # Invocacion asincrona (trigger S3): AWS reintenta 2 veces automaticamente
  # sin configurar nada — si sigue fallando (ej: PDF corrupto que sigue sin
  # poder procesarse), el payload del evento cae aca.
  dead_letter_config {
    target_arn = var.dlq_arn
  }
}
