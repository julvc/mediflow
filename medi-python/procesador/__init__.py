"""Nucleo de procesamiento de documentos de MediFlow — agnostico de nube y
de framework web: no importa boto3, google-cloud-storage ni FastAPI. Los
adaptadores de evento/HTTP viven fuera de este paquete y lo invocan, nunca
al reves.
"""

from .dominio import DocumentoInvalidoError, ResultadoProcesamiento
from .implementaciones import ExtractorMetadataPyPDF, GeneradorThumbnailPyMuPDF
from .servicio import ProcesadorDocumentoService

__all__ = [
    "DocumentoInvalidoError",
    "ResultadoProcesamiento",
    "ProcesadorDocumentoService",
    "crear_procesador_por_defecto",
]


def crear_procesador_por_defecto() -> ProcesadorDocumentoService:
    """Composition root: la unica funcion del paquete que conoce las
    implementaciones concretas. Todo lo demas depende de las interfaces."""
    return ProcesadorDocumentoService(
        extractor=ExtractorMetadataPyPDF(),
        generador_thumbnail=GeneradorThumbnailPyMuPDF(),
    )
