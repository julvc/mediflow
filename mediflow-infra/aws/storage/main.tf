resource "aws_s3_bucket" "documentos" {
  bucket = "mediflow-documentos"
}

resource "aws_s3_bucket_versioning" "documentos" {
  bucket = aws_s3_bucket.documentos.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Los documentos son PDFs de referencia, no el registro permanente — el
# registro real vive en DynamoDB. 7 dias alcanza para reprocesar si algo
# falla, sin acumular storage indefinido.
resource "aws_s3_bucket_lifecycle_configuration" "documentos" {
  bucket = aws_s3_bucket.documentos.id
  rule {
    id     = "expirar-a-7-dias"
    status = "Enabled"
    filter {}
    expiration {
      days = 7
    }
  }

  # transition_default_minimum_object_size es un default del provider AWS v5
  # que LocalStack Community no persiste/devuelve: sin esto, cada plan/apply
  # intenta "corregirlo" y el waiter de este recurso nunca confirma el estado
  # contra LocalStack (timeout de 3min, visto tanto en create como en update).
  lifecycle {
    ignore_changes = [transition_default_minimum_object_size]
  }
}

resource "aws_s3_bucket_public_access_block" "documentos" {
  bucket                  = aws_s3_bucket.documentos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
