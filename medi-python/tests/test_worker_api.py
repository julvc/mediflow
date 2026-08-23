import base64
import io

from fastapi.testclient import TestClient
from pypdf import PdfWriter

from worker_api import app

client = TestClient(app)


def _pdf_valido() -> bytes:
    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    buffer = io.BytesIO()
    writer.write(buffer)
    return buffer.getvalue()


def test_procesar_pdf_valido_devuelve_metadata_y_thumbnail_en_base64():
    respuesta = client.post(
        "/procesar",
        files={"archivo": ("examen.pdf", _pdf_valido(), "application/pdf")},
    )

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["nombreArchivo"] == "examen.pdf"
    assert cuerpo["paginas"] == 1
    assert base64.b64decode(cuerpo["thumbnailPngBase64"]).startswith(b"\x89PNG")


def test_procesar_archivo_invalido_devuelve_422():
    respuesta = client.post(
        "/procesar",
        files={"archivo": ("falso.pdf", b"esto no es un PDF", "application/pdf")},
    )

    assert respuesta.status_code == 422
