from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..bd import get_db
from ..dependencias import UsuarioActual, get_usuario_actual, requiere_roles
from ..dominio import STAFF, Rol
from ..esquemas import CambioEstadoDocumento, DocumentoCrear, DocumentoResponse
from ..turnos.repositorio import SQLAlchemyTurnoRepository
from .repositorio import SQLAlchemyDocumentoRepository
from .servicio import DocumentoService

router = APIRouter(prefix="/documentos", tags=["documentos"], dependencies=[Depends(get_usuario_actual)])


def get_documento_service(db: Session = Depends(get_db)) -> DocumentoService:
    return DocumentoService(SQLAlchemyDocumentoRepository(db), SQLAlchemyTurnoRepository(db))


@router.post("", response_model=DocumentoResponse, status_code=status.HTTP_201_CREATED)
def crear(datos: DocumentoCrear, servicio: DocumentoService = Depends(get_documento_service)):
    return servicio.crear(datos)


# turnoId obligatorio y solo staff: listar por turno es el unico caso de uso
# pedido, y un PACIENTE nunca tiene "mis documentos" (mismo comentario y regla
# que DocumentoController.listarPorTurno en Java).
@router.get("", response_model=list[DocumentoResponse])
def listar_por_turno(
    turno_id: int,
    servicio: DocumentoService = Depends(get_documento_service),
    _usuario: UsuarioActual = Depends(requiere_roles(*STAFF)),
):
    return servicio.listar_por_turno(turno_id)


@router.get("/{id}", response_model=DocumentoResponse)
def obtener(
    id: int,
    servicio: DocumentoService = Depends(get_documento_service),
    usuario: UsuarioActual = Depends(get_usuario_actual),
):
    # El dueno real es el Paciente del Turno del Documento — dato que solo se
    # conoce despues de cargarlo, mismo patron de ownership "de dos saltos"
    # que @recursoAuth.esPropioPaciente(..., returnObject.pacienteId()) en Java.
    documento = servicio.obtener_por_id(id)
    if usuario.rol not in STAFF and documento.turno.paciente_id != usuario.paciente_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permiso para esta operación")
    return documento


@router.patch("/{id}/estado", response_model=DocumentoResponse)
def cambiar_estado(
    id: int,
    datos: CambioEstadoDocumento,
    servicio: DocumentoService = Depends(get_documento_service),
    _usuario: UsuarioActual = Depends(requiere_roles(*STAFF)),
):
    return servicio.cambiar_estado(id, datos.estado)


@router.delete("/{id}", status_code=status.HTTP_204_NO_CONTENT)
def eliminar(
    id: int,
    servicio: DocumentoService = Depends(get_documento_service),
    _usuario: UsuarioActual = Depends(requiere_roles(Rol.ADMIN)),
):
    servicio.eliminar(id)
