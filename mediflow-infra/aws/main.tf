locals {
  nombre_funcion = "mediflow-worker"
}

module "storage" {
  source = "./storage"
}

module "data" {
  source = "./data"
}

module "iam" {
  source = "./iam"

  bucket_arn     = module.storage.bucket_arn
  tabla_arn      = module.data.tabla_arn
  dlq_arn        = module.data.dlq_arn
  secret_arn     = module.data.secret_arn
  nombre_funcion = local.nombre_funcion
}

module "compute" {
  source = "./compute"

  nombre_funcion = local.nombre_funcion
  role_arn       = module.iam.role_arn
  tabla_nombre   = module.data.tabla_nombre
  secret_arn     = module.data.secret_arn
  dlq_arn        = module.data.dlq_arn
}

# Permiso para que S3 invoque la Lambda, y la notificacion en si — viven en
# el root (no en storage/ ni compute/) porque son la union de ambos modulos,
# ninguno de los dos deberia conocer al otro directamente.
resource "aws_lambda_permission" "s3_invoca_worker" {
  statement_id  = "PermitirInvocacionDesdeS3"
  action        = "lambda:InvokeFunction"
  function_name = module.compute.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = module.storage.bucket_arn
}

resource "aws_s3_bucket_notification" "documentos_subidos" {
  bucket = module.storage.bucket_id

  lambda_function {
    lambda_function_arn = module.compute.function_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "inbox/"
  }

  depends_on = [aws_lambda_permission.s3_invoca_worker]
}
