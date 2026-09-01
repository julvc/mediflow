from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from ..bd import get_db
from ..dependencias import UsuarioActual, get_usuario_actual_opcional
from ..esquemas import LoginRequest, RefreshRequest, RegistroRequest, TokenResponse
from ..pacientes.repositorio import SQLAlchemyPacienteRepository
from ..profesionales.repositorio import SQLAlchemyProfesionalRepository
from .servicio import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])


def get_auth_service(db: Session = Depends(get_db)) -> AuthService:
    return AuthService(db, SQLAlchemyPacienteRepository(db), SQLAlchemyProfesionalRepository(db))


@router.post("/registro", status_code=status.HTTP_201_CREATED)
def registro(
    datos: RegistroRequest,
    solicitante: UsuarioActual | None = Depends(get_usuario_actual_opcional),
    servicio: AuthService = Depends(get_auth_service),
):
    usuario = servicio.registrar(datos, solicitante)
    return {"id": usuario.id, "email": usuario.email, "rol": usuario.rol}


@router.post("/login", response_model=TokenResponse)
def login(datos: LoginRequest, servicio: AuthService = Depends(get_auth_service)):
    access_token, refresh_token, expira_en_segundos = servicio.login(datos)
    return TokenResponse(access_token=access_token, refresh_token=refresh_token, expira_en_segundos=expira_en_segundos)


@router.post("/refresh", response_model=TokenResponse)
def refrescar(datos: RefreshRequest, servicio: AuthService = Depends(get_auth_service)):
    access_token, refresh_token, expira_en_segundos = servicio.refrescar(datos.refresh_token)
    return TokenResponse(access_token=access_token, refresh_token=refresh_token, expira_en_segundos=expira_en_segundos)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(datos: RefreshRequest, servicio: AuthService = Depends(get_auth_service)):
    servicio.logout(datos.refresh_token)
