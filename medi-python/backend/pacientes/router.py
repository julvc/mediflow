from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from ..bd import get_db
from ..dependencias import get_usuario_actual
from ..esquemas import PacienteActualizar, PacienteCrear, PacienteResponse
from .repositorio import SQLAlchemyPacienteRepository
from .servicio import PacienteService

router = APIRouter(prefix="/pacientes", tags=["pacientes"], dependencies=[Depends(get_usuario_actual)])


def get_paciente_service(db: Session = Depends(get_db)) -> PacienteService:
    """Composition root del router: unico lugar que conoce la implementacion
    concreta del repositorio."""
    return PacienteService(SQLAlchemyPacienteRepository(db))


@router.post("", response_model=PacienteResponse, status_code=status.HTTP_201_CREATED)
def crear(datos: PacienteCrear, servicio: PacienteService = Depends(get_paciente_service)):
    return servicio.crear(datos)


@router.get("", response_model=list[PacienteResponse])
def listar(servicio: PacienteService = Depends(get_paciente_service)):
    return servicio.listar_todos()


@router.get("/{id}", response_model=PacienteResponse)
def obtener(id: int, servicio: PacienteService = Depends(get_paciente_service)):
    return servicio.obtener_por_id(id)


@router.put("/{id}", response_model=PacienteResponse)
def actualizar(id: int, datos: PacienteActualizar, servicio: PacienteService = Depends(get_paciente_service)):
    return servicio.actualizar(id, datos)


@router.delete("/{id}", status_code=status.HTTP_204_NO_CONTENT)
def eliminar(id: int, servicio: PacienteService = Depends(get_paciente_service)):
    servicio.eliminar(id)
