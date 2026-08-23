"""Adaptador HTTP del worker — el punto de entrada que usa el backend Java
(que no puede importar procesador/ directamente). El backend Python en
cambio importa procesador/ como libreria, sin pasar por HTTP consigo mismo.

Deliberadamente separado de backend/: en la nube real, este mismo modulo
(envuelto en un adaptador de evento S3/Eventarc en vez de HTTP) es el que se
despliega como Lambda/Cloud Run Function — no debe arrastrar SQLAlchemy, JWT
ni nada del backend REST.
"""

import base64

from fastapi import FastAPI, HTTPException, UploadFile

from procesador import DocumentoInvalidoError, crear_procesador_por_defecto

app = FastAPI(title="MediFlow — worker de documentos")
_procesador = crear_procesador_por_defecto()


@app.post("/procesar")
async def procesar(archivo: UploadFile) -> dict:
    contenido = await archivo.read()
    try:
        resultado = _procesador.procesar(contenido, archivo.filename or "documento.pdf")
    except DocumentoInvalidoError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    return {
        "nombreArchivo": resultado.nombre_archivo,
        "tamanoBytes": resultado.tamano_bytes,
        "paginas": resultado.paginas,
        "titulo": resultado.titulo,
        "autor": resultado.autor,
        "thumbnailPngBase64": base64.b64encode(resultado.thumbnail_png).decode("ascii"),
    }
