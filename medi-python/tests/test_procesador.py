import io

import pytest
from pypdf import PdfWriter

from procesador import DocumentoInvalidoError, crear_procesador_por_defecto


def _pdf_valido(paginas=1) -> bytes:
    writer = PdfWriter()
    for _ in range(paginas):
        writer.add_blank_page(width=200, height=200)
    writer.add_metadata({"/Title": "Examen de sangre", "/Author": "Dra. Ana Soto"})
    buffer = io.BytesIO()
    writer.write(buffer)
    return buffer.getvalue()


@pytest.fixture
def procesador():
    return crear_procesador_por_defecto()


def test_procesar_pdf_valido_extrae_metadata_y_thumbnail(procesador):
    resultado = procesador.procesar(_pdf_valido(paginas=3), "examen.pdf")

    assert resultado.nombre_archivo == "examen.pdf"
    assert resultado.paginas == 3
    assert resultado.titulo == "Examen de sangre"
    assert resultado.autor == "Dra. Ana Soto"
    assert resultado.thumbnail_png.startswith(b"\x89PNG")


def test_procesar_sin_magic_bytes_lanza_documento_invalido(procesador):
    with pytest.raises(DocumentoInvalidoError):
        procesador.procesar(b"esto no es un PDF", "falso.pdf")


def test_procesar_pdf_truncado_lanza_documento_invalido(procesador):
    pdf_truncado = _pdf_valido()[:20]  # magic bytes correctos, resto corrupto

    with pytest.raises(DocumentoInvalidoError):
        procesador.procesar(pdf_truncado, "corrupto.pdf")
