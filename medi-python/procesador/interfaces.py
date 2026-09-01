"""Abstracciones (DIP): ProcesadorDocumentoService depende de estas interfaces,
nunca de pypdf/pymupdf directamente. Cambiar el motor de metadata o de
thumbnail (ej: pdfium en vez de pymupdf) es una implementacion nueva, no un
cambio en el servicio que las orquesta."""

from typing import Protocol


class ExtractorMetadata(Protocol):
    def paginas_y_metadata(self, contenido: bytes, nombre_archivo: str) -> tuple[int, dict]:
        """Devuelve (cantidad_de_paginas, metadata_dict). Lanza DocumentoInvalidoError
        si el contenido esta corrupto."""
        ...


class GeneradorThumbnail(Protocol):
    def generar(self, contenido: bytes, nombre_archivo: str) -> bytes:
        """Devuelve los bytes de un PNG con la primera pagina rasterizada.
        Lanza DocumentoInvalidoError si el contenido esta corrupto."""
        ...
