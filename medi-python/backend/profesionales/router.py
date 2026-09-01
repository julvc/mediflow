from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..bd import get_db
from ..dependencias import UsuarioActual, get_usuario_actual, requiere_roles
from ..dominio import STAFF, Rol
from ..esquemas import ProfesionalActualizar, ProfesionalCrear, ProfesionalResponse
from .repositorio import SQLAlchemyProfesionalRepository
from .servicio import ProfesionalService

router = APIRouter(prefix="/profesionales", tags=["profesionales"], dependencies=[Depends(get_usuario_actual)])


def get_profesional_service(db: Session = Depends(get_db)) -> ProfesionalService:
    return ProfesionalService(SQLAlchemyProfesionalRepository(db))


@router.post("", response_model=ProfesionalResponse, status_code=status.HTTP_201_CREATED)
def crear(
    datos: ProfesionalCrear,
    servicio: ProfesionalService = Depends(get_profesional_service),
    _usuario: UsuarioActual = Depends(requiere_roles(Rol.ADMIN)),
):
    return servicio.crear(datos)


# Listado abierto a proposito: un PACIENTE necesita poder listar profesionales
# para agendar un turno — mismo comentario/regla que ProfesionalController en Java.
@router.get("", response_model=list[ProfesionalResponse])
def listar(servicio: ProfesionalService = Depends(get_profesional_service)):
    return servicio.listar_todos()


@router.get("/{id}", response_model=ProfesionalResponse)
def obtener(id: int, servicio: ProfesionalService = Depends(get_profesional_service)):
    return servicio.obtener_por_id(id)


@router.put("/{id}", response_model=ProfesionalResponse)
def actualizar(
    id: int,
    datos: ProfesionalActualizar,
    servicio: ProfesionalService = Depends(get_profesional_service),
    usuario: UsuarioActual = Depends(get_usuario_actual),
):
    if usuario.rol != Rol.ADMIN and usuario.profesional_id != id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permiso para esta operación")
    return servicio.actualizar(id, datos)


@router.delete("/{id}", status_code=status.HTTP_204_NO_CONTENT)
def eliminar(
    id: int,
    servicio: ProfesionalService = Depends(get_profesional_service),
    _usuario: UsuarioActual = Depends(requiere_roles(Rol.ADMIN)),
):
    servicio.eliminar(id)
