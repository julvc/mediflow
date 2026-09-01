from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..bd import get_db
from ..dependencias import UsuarioActual, get_usuario_actual, requiere_roles
from ..dominio import STAFF, Rol
from ..esquemas import PacienteActualizar, PacienteCrear, PacienteResponse
from .repositorio import SQLAlchemyPacienteRepository
from .servicio import PacienteService

router = APIRouter(prefix="/pacientes", tags=["pacientes"])


def get_paciente_service(db: Session = Depends(get_db)) -> PacienteService:
    """Composition root del router: unico lugar que conoce la implementacion
    concreta del repositorio."""
    return PacienteService(SQLAlchemyPacienteRepository(db))


@router.post("", response_model=PacienteResponse, status_code=status.HTTP_201_CREATED)
def crear(
    datos: PacienteCrear,
    servicio: PacienteService = Depends(get_paciente_service),
    _usuario: UsuarioActual = Depends(requiere_roles(*STAFF)),
):
    return servicio.crear(datos)


@router.get("", response_model=list[PacienteResponse])
def listar(
    servicio: PacienteService = Depends(get_paciente_service),
    _usuario: UsuarioActual = Depends(requiere_roles(*STAFF)),
):
    return servicio.listar_todos()


@router.get("/{id}", response_model=PacienteResponse)
def obtener(
    id: int,
    servicio: PacienteService = Depends(get_paciente_service),
    usuario: UsuarioActual = Depends(get_usuario_actual),
):
    # Staff ve cualquier ficha; un PACIENTE solo la suya — mismo chequeo que
    # @recursoAuth.esPropioPaciente en Java.
    if usuario.rol not in STAFF and usuario.paciente_id != id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permiso para esta operación")
    return servicio.obtener_por_id(id)


@router.put("/{id}", response_model=PacienteResponse)
def actualizar(
    id: int,
    datos: PacienteActualizar,
    servicio: PacienteService = Depends(get_paciente_service),
    _usuario: UsuarioActual = Depends(requiere_roles(*STAFF)),
):
    return servicio.actualizar(id, datos)


@router.delete("/{id}", status_code=status.HTTP_204_NO_CONTENT)
def eliminar(
    id: int,
    servicio: PacienteService = Depends(get_paciente_service),
    _usuario: UsuarioActual = Depends(requiere_roles(Rol.ADMIN)),
):
    servicio.eliminar(id)
