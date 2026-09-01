"""SQLite de archivo en vez de Postgres para estos tests: rapido, sin Docker,
suficiente para probar reglas de negocio y capas HTTP. A diferencia de
medi-java (Testcontainers contra Postgres real, para validar restricciones
que solo existen en la base), aqui no hay ninguna regla especifica de
Postgres que probar todavia — si aparece una (ej: un indice unico parcial),
se agrega un fixture aparte con testcontainers-python.
"""

import itertools
import os
from datetime import datetime, timedelta, timezone
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
    """Login como el ADMIN semilla (creado por el lifespan al arrancar la
    app, mismo email/password que V2__auth_schema.sql en Java) — la mayoria
    de los tests de dominio necesitan permisos de staff, no la identidad
    especifica del usuario."""
    respuesta = client.post("/auth/login", json={"email": "admin@mediflow.cl", "password": "admin1234"})
    return respuesta.json()["access_token"]


@pytest.fixture
def headers_auth(token_autenticado):
    return {"Authorization": f"Bearer {token_autenticado}"}


_contador_rut = itertools.count(10000000)


def _rut_unico() -> str:
    """RUT unico por llamada — evita colisiones cuando un fixture que crea
    pacientes/profesionales se reusa en varios tests dentro del mismo run
    (la DB persiste entre tests dentro de la sesion)."""
    return f"{next(_contador_rut)}-5"


@pytest.fixture
def paciente_y_profesional(client, headers_auth) -> tuple[int, int]:
    paciente = client.post(
        "/pacientes",
        json={
            "rut": _rut_unico(),
            "nombres": "Carla",
            "apellidos": "Vega",
            "fecha_nacimiento": "1992-05-20",
            "email": f"carla{next(_contador_rut)}@test.cl",
        },
        headers=headers_auth,
    ).json()
    profesional = client.post(
        "/profesionales",
        json={
            "rut": _rut_unico(),
            "nombres": "Ana",
            "apellidos": "Soto",
            "especialidad": "Kinesiología",
            "email": f"ana{next(_contador_rut)}@test.cl",
        },
        headers=headers_auth,
    ).json()
    return paciente["id"], profesional["id"]


@pytest.fixture
def paciente_autenticado(client, headers_auth) -> tuple[int, dict]:
    """Crea una ficha de Paciente (como admin) y una cuenta PACIENTE vinculada
    a ella — para probar las restricciones de rol desde el lado del paciente,
    no del staff."""
    email = f"paciente{next(_contador_rut)}@test.cl"
    paciente = client.post(
        "/pacientes",
        json={
            "rut": _rut_unico(),
            "nombres": "Permisos",
            "apellidos": "Test",
            "fecha_nacimiento": "1990-01-01",
            "email": email,
        },
        headers=headers_auth,
    ).json()

    client.post(
        "/auth/registro",
        json={"email": email, "password": "clave1234", "rol": "PACIENTE", "paciente_id": paciente["id"]},
    )
    token = client.post("/auth/login", json={"email": email, "password": "clave1234"}).json()["access_token"]
    return paciente["id"], {"Authorization": f"Bearer {token}"}


@pytest.fixture
def turno_id(client, headers_auth, paciente_y_profesional) -> int:
    paciente_id, profesional_id = paciente_y_profesional
    fecha_futura = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    respuesta = client.post(
        "/turnos",
        json={"paciente_id": paciente_id, "profesional_id": profesional_id, "fecha_hora": fecha_futura},
        headers=headers_auth,
    )
    return respuesta.json()["id"]
