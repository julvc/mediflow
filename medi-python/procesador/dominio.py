"""Tipos de dominio del procesador: sin dependencias de pypdf/pymupdf/FastAPI."""

from dataclasses import dataclass


class DocumentoInvalidoError(Exception):
    """El contenido no es un PDF valido o esta corrupto."""


@dataclass(frozen=True)
class ResultadoProcesamiento:
    nombre_archivo: str
    tamano_bytes: int
    paginas: int
    titulo: str | None
    autor: str | None
    thumbnail_png: bytes
