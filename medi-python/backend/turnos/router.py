from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..bd import get_db
from ..dependencias import UsuarioActual, get_usuario_actual
from ..dominio import STAFF
from ..esquemas import CambioEstadoTurno, TurnoCrear, TurnoResponse
from ..pacientes.repositorio import SQLAlchemyPacienteRepository
from ..profesionales.repositorio import SQLAlchemyProfesionalRepository
from .repositorio import SQLAlchemyTurnoRepository
from .servicio import TurnoService

router = APIRouter(prefix="/turnos", tags=["turnos"], dependencies=[Depends(get_usuario_actual)])


def get_turno_service(db: Session = Depends(get_db)) -> TurnoService:
    return TurnoService(
        SQLAlchemyTurnoRepository(db),
        SQLAlchemyPacienteRepository(db),
        SQLAlchemyProfesionalRepository(db),
    )


@router.post("", response_model=TurnoResponse, status_code=status.HTTP_201_CREATED)
def crear(datos: TurnoCrear, servicio: TurnoService = Depends(get_turno_service)):
    return servicio.crear(datos)


@router.get("", response_model=list[TurnoResponse])
def listar(
    paciente_id: int | None = None,
    servicio: TurnoService = Depends(get_turno_service),
    usuario: UsuarioActual = Depends(get_usuario_actual),
):
    # Un PACIENTE solo puede listar los suyos (pasando su propio paciente_id);
    # sin paciente_id, o con uno ajeno, cae en 403 — mismo chequeo que
    # TurnoController.listar en Java.
    if usuario.rol not in STAFF:
        if paciente_id is None or paciente_id != usuario.paciente_id:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permiso para esta operación")
    return servicio.listar_por_paciente(paciente_id) if paciente_id is not None else servicio.listar_todos()


@router.get("/{id}", response_model=TurnoResponse)
def obtener(
    id: int,
    servicio: TurnoService = Depends(get_turno_service),
    usuario: UsuarioActual = Depends(get_usuario_actual),
):
    turno = servicio.obtener_por_id(id)
    if usuario.rol not in STAFF and turno.paciente_id != usuario.paciente_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permiso para esta operación")
    return turno


@router.patch("/{id}/estado", response_model=TurnoResponse)
def cambiar_estado(
    id: int,
    datos: CambioEstadoTurno,
    servicio: TurnoService = Depends(get_turno_service),
    usuario: UsuarioActual = Depends(get_usuario_actual),
):
    return servicio.cambiar_estado(id, datos.estado, usuario)
