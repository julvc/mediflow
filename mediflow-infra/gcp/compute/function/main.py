"""Adaptador de evento GCS -> procesador/. El unico modulo de todo el worker
que importa google-cloud-storage o pg8000 — procesador/ en si sigue siendo
agnostico de nube, sin cambios, copiado tal cual de medi-python (ver build.sh).

Espejo de aws/compute/lambda/handler.py con dos diferencias de plataforma:
Eventarc entrega un CloudEvent (no un batch de Records como S3), y el
registro va a Cloud SQL/Postgres (relacional) en vez de DynamoDB — el mismo
dominio modelado dos formas distintas, a proposito (ver CLAUDE.md).

Se conecta por TCP a la IP privada (DB_HOST), no por socket Unix: a
diferencia de Cloud Run v2 (mediflow-api), Cloud Run Functions gen2 no
soporta el mismo mount de /cloudsql, así que el camino real es el conector
de VPC (mediflow-infra/gcp/data/main.tf) + IP privada de la instancia.
"""

import base64
import os

import functions_framework
import pg8000.native
from google.cloud import storage

from procesador import crear_procesador_por_defecto

_storage = storage.Client()
_procesador = crear_procesador_por_defecto()

_CREAR_TABLA = """
CREATE TABLE IF NOT EXISTS documentos_procesados (
    id SERIAL PRIMARY KEY,
    bucket TEXT NOT NULL,
    objeto TEXT NOT NULL UNIQUE,
    nombre_archivo TEXT NOT NULL,
    tamano_bytes INTEGER NOT NULL,
    paginas INTEGER NOT NULL,
    titulo TEXT,
    autor TEXT,
    thumbnail_png_base64 TEXT NOT NULL
)
"""


@functions_framework.cloud_event
def procesar_documento(cloud_event):
    data = cloud_event.data
    bucket_nombre = data["bucket"]
    objeto = data["name"]
    nombre_archivo = objeto.rsplit("/", 1)[-1]

    contenido = _storage.bucket(bucket_nombre).blob(objeto).download_as_bytes()

    # Igual que en Lambda: si el PDF es invalido, procesar() lanza
    # DocumentoInvalidoError y la dejamos propagar. Eventarc/Cloud Run
    # reintenta segun la politica de reintentos de la suscripcion Pub/Sub
    # subyacente y, agotados, entrega al dead-letter topic configurado —
    # sin try/except artificial en este handler.
    resultado = _procesador.procesar(contenido, nombre_archivo)

    conexion = pg8000.native.Connection(
        user="mediflow_app",
        password=os.environ["DB_PASSWORD"],
        database="mediflow",
        host=os.environ["DB_HOST"],
        port=5432,
    )
    try:
        conexion.run(_CREAR_TABLA)
        conexion.run(
            """
            INSERT INTO documentos_procesados
                (bucket, objeto, nombre_archivo, tamano_bytes, paginas, titulo, autor, thumbnail_png_base64)
            VALUES (:bucket, :objeto, :nombre, :tamano, :paginas, :titulo, :autor, :thumb)
            ON CONFLICT (objeto) DO NOTHING
            """,
            bucket=bucket_nombre,
            objeto=objeto,
            nombre=resultado.nombre_archivo,
            tamano=resultado.tamano_bytes,
            paginas=resultado.paginas,
            titulo=resultado.titulo,
            autor=resultado.autor,
            thumb=base64.b64encode(resultado.thumbnail_png).decode("ascii"),
        )
    finally:
        conexion.close()
