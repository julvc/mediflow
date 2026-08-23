"""Implementaciones concretas de las interfaces del procesador (SRP: cada
clase tiene una sola razon para cambiar — la libreria que envuelve)."""

import io

import pymupdf
import pypdf
from PIL import Image

from .dominio import DocumentoInvalidoError

ANCHO_THUMBNAIL = 200


class ExtractorMetadataPyPDF:
    """Implementa ExtractorMetadata con pypdf."""

    def paginas_y_metadata(self, contenido: bytes, nombre_archivo: str) -> tuple[int, dict]:
        try:
            lector = pypdf.PdfReader(io.BytesIO(contenido))
            paginas = len(lector.pages)
        except pypdf.errors.PdfReadError as exc:
            raise DocumentoInvalidoError(f"{nombre_archivo}: PDF corrupto ({exc})") from exc

        if paginas == 0:
            raise DocumentoInvalidoError(f"{nombre_archivo}: el PDF no tiene paginas")

        return paginas, dict(lector.metadata or {})


class GeneradorThumbnailPyMuPDF:
    """Implementa GeneradorThumbnail con pymupdf (rasteriza) + Pillow (resize/encode).

    pypdf no puede rasterizar paginas (solo lee texto/metadata) y Pillow no
    puede leer PDF por si solo — pymupdf es lo que realmente convierte la
    pagina en pixeles.
    """

    def __init__(self, ancho_maximo: int = ANCHO_THUMBNAIL):
        self._ancho_maximo = ancho_maximo

    def generar(self, contenido: bytes, nombre_archivo: str) -> bytes:
        try:
            with pymupdf.open(stream=contenido, filetype="pdf") as doc:
                pixmap = doc[0].get_pixmap()
        except pymupdf.FileDataError as exc:
            raise DocumentoInvalidoError(f"{nombre_archivo}: PDF corrupto ({exc})") from exc

        imagen = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
        imagen.thumbnail((self._ancho_maximo, self._ancho_maximo))

        buffer = io.BytesIO()
        imagen.save(buffer, format="PNG")
        return buffer.getvalue()
