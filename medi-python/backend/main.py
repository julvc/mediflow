from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .auth.router import router as auth_router
from .bd import Base, engine
from .pacientes.router import router as pacientes_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # create_all en vez de Alembic a proposito: mismo espiritu POC que el
    # resto del proyecto. Si el esquema empieza a evolucionar con datos reales
    # que conservar, ahi se justifica una herramienta de migraciones real.
    Base.metadata.create_all(bind=engine)
    yield


app = FastAPI(title="MediFlow — backend Python (paralelo a medi-java)", lifespan=lifespan)

app.include_router(auth_router)
app.include_router(pacientes_router)


@app.exception_handler(RequestValidationError)
def manejar_error_validacion(request: Request, exc: RequestValidationError) -> JSONResponse:
    """Misma forma de error que medi-java (ErrorResponse.detalles), para que
    ambos backends sean comparables desde el punto de vista de un cliente."""
    detalles = [f"{'.'.join(str(p) for p in e['loc'][1:])}: {e['msg']}" for e in exc.errors()]
    return JSONResponse(
        status_code=422,
        content={"status": 422, "error": "Unprocessable Entity", "message": "Error de validación", "detalles": detalles},
    )
