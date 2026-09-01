"""Adaptador de evento S3 -> procesador/. El unico modulo de todo el worker
que importa boto3 o sabe que existe S3/DynamoDB — procesador/ en si sigue
siendo agnostico de nube, sin cambios, copiado tal cual de medi-python.

boto3 no va en requirements.txt: el runtime de Lambda ya lo trae preinstalado,
empaquetarlo de nuevo solo infla el zip sin necesidad.
"""

import base64
import os
import urllib.parse

import boto3

from procesador import crear_procesador_por_defecto

_s3 = boto3.client("s3")
_dynamodb = boto3.resource("dynamodb")
_procesador = crear_procesador_por_defecto()


def procesar_documento(event, context):
    tabla = _dynamodb.Table(os.environ["TABLA_DOCUMENTOS"])
    procesados = []

    for record in event["Records"]:
        bucket = record["s3"]["bucket"]["name"]
        key = urllib.parse.unquote_plus(record["s3"]["object"]["key"])
        nombre_archivo = key.rsplit("/", 1)[-1]

        contenido = _s3.get_object(Bucket=bucket, Key=key)["Body"].read()

        # Si el PDF es invalido, procesar() lanza DocumentoInvalidoError y la
        # dejamos propagar: Lambda marca la invocacion como fallida, agota los
        # 2 reintentos automaticos de la invocacion asincrona (default de AWS,
        # sin config extra) y el evento cae en la DLQ configurada en
        # dead_letter_config — sin try/except artificial en este handler.
        resultado = _procesador.procesar(contenido, nombre_archivo)

        # Single-table design: PK/SK en vez de una tabla por entidad — un
        # documento con varias facetas (metadata, paginas futuras) cabe en
        # la misma partition key con distinto sort key, sin joins.
        tabla.put_item(
            Item={
                "PK": f"DOC#{key}",
                "SK": "META#",
                "nombreArchivo": resultado.nombre_archivo,
                "tamanoBytes": resultado.tamano_bytes,
                "paginas": resultado.paginas,
                "titulo": resultado.titulo,
                "autor": resultado.autor,
                "thumbnailPngBase64": _b64(resultado.thumbnail_png),
            }
        )
        procesados.append(key)

    return {"procesados": procesados}


def _b64(contenido: bytes) -> str:
    return base64.b64encode(contenido).decode("ascii")
