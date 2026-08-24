from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .auth.router import router as auth_router
from .bd import Base, SessionLocal, engine
from .documentos.router import router as documentos_router
from .dominio import Rol
from .modelos import Usuario
from .pacientes.router import router as pacientes_router
from .profesionales.router import router as profesionales_router
from .seguridad import hashear_password
from .turnos.router import router as turnos_router

# ADMIN semilla: sin esto no hay forma de crear el primer ADMIN, porque
# POST /auth/registro con rol=ADMIN exige ya estar autenticado como ADMIN —
# mismo problema y misma solucion que V2__auth_schema.sql en Java (ahi via
# INSERT en la migracion; aca, sin Alembic, al arrancar la app).
_ADMIN_SEMILLA_EMAIL = "admin@mediflow.cl"
_ADMIN_SEMILLA_PASSWORD = "admin1234"


def _sembrar_admin() -> None:
    with SessionLocal() as db:
        ya_existe = db.query(Usuario).filter(Usuario.email == _ADMIN_SEMILLA_EMAIL).first() is not None
        if ya_existe:
            return
        db.add(Usuario(email=_ADMIN_SEMILLA_EMAIL, password_hash=hashear_password(_ADMIN_SEMILLA_PASSWORD), rol=Rol.ADMIN))
        db.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # create_all en vez de Alembic a proposito: mismo espiritu POC que el
    # resto del proyecto. Si el esquema empieza a evolucionar con datos reales
    # que conservar, ahi se justifica una herramienta de migraciones real.
    Base.metadata.create_all(bind=engine)
    _sembrar_admin()
    yield


app = FastAPI(title="MediFlow — backend Python (paralelo a medi-java)", lifespan=lifespan)

app.include_router(auth_router)
app.include_router(pacientes_router)
app.include_router(profesionales_router)
app.include_router(turnos_router)
app.include_router(documentos_router)


@app.exception_handler(RequestValidationError)
def manejar_error_validacion(request: Request, exc: RequestValidationError) -> JSONResponse:
    """Misma forma de error que medi-java (ErrorResponse.detalles), para que
    ambos backends sean comparables desde el punto de vista de un cliente."""
    detalles = [f"{'.'.join(str(p) for p in e['loc'][1:])}: {e['msg']}" for e in exc.errors()]
    return JSONResponse(
        status_code=422,
        content={"status": 422, "error": "Unprocessable Entity", "message": "Error de validación", "detalles": detalles},
    )
