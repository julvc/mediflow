"""Orquestador (SRP + DIP): coordina validacion, metadata y thumbnail sin
saber que libreria hay detras de cada uno — eso lo deciden las
implementaciones inyectadas."""

from .dominio import DocumentoInvalidoError, ResultadoProcesamiento
from .interfaces import ExtractorMetadata, GeneradorThumbnail

MAGIC_BYTES_PDF = b"%PDF-"


class ProcesadorDocumentoService:
    def __init__(self, extractor: ExtractorMetadata, generador_thumbnail: GeneradorThumbnail):
        self._extractor = extractor
        self._generador_thumbnail = generador_thumbnail

    def procesar(self, contenido: bytes, nombre_archivo: str) -> ResultadoProcesamiento:
        if not contenido.startswith(MAGIC_BYTES_PDF):
            raise DocumentoInvalidoError(f"{nombre_archivo}: no es un PDF valido (magic bytes)")

        paginas, metadata = self._extractor.paginas_y_metadata(contenido, nombre_archivo)
        thumbnail = self._generador_thumbnail.generar(contenido, nombre_archivo)

        return ResultadoProcesamiento(
            nombre_archivo=nombre_archivo,
            tamano_bytes=len(contenido),
            paginas=paginas,
            titulo=metadata.get("/Title"),
            autor=metadata.get("/Author"),
            thumbnail_png=thumbnail,
        )
