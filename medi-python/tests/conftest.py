"""SQLite de archivo en vez de Postgres para estos tests: rapido, sin Docker,
suficiente para probar reglas de negocio y capas HTTP. A diferencia de
medi-java (Testcontainers contra Postgres real, para validar restricciones
que solo existen en la base), aqui no hay ninguna regla especifica de
Postgres que probar todavia — si aparece una (ej: un indice unico parcial),
se agrega un fixture aparte con testcontainers-python.
"""

import os
from pathlib import Path

_DB_PATH = Path(__file__).parent / "_test.db"
os.environ["MEDIFLOW_PY_DATABASE_URL"] = f"sqlite:///{_DB_PATH}"

import pytest
from fastapi.testclient import TestClient

from backend.bd import engine  # noqa: E402  (import tardio: necesita el env var ya seteado)
from backend.main import app  # noqa: E402


@pytest.fixture(scope="session", autouse=True)
def _limpiar_db_al_final():
    yield
    engine.dispose()  # libera el handle del archivo antes de borrarlo (Windows lo bloquea si sigue abierto)
    _DB_PATH.unlink(missing_ok=True)


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


@pytest.fixture
def token_autenticado(client):
    """Registra e inicia sesion con un usuario nuevo, devuelve el access token."""
    email = "test@mediflow.cl"
    password = "clave1234"
    client.post("/auth/registro", json={"email": email, "password": password})
    respuesta = client.post("/auth/login", json={"email": email, "password": password})
    return respuesta.json()["access_token"]


@pytest.fixture
def headers_auth(token_autenticado):
    return {"Authorization": f"Bearer {token_autenticado}"}
