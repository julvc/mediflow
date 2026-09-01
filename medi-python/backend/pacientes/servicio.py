from fastapi import HTTPException, status

from ..esquemas import PacienteActualizar, PacienteCrear
from ..modelos import Paciente
from .repositorio import PacienteRepository


class PacienteService:
    """Logica de negocio (SRP): valida reglas de dominio y delega la
    persistencia al repositorio inyectado — nunca usa SQLAlchemy directo."""

    def __init__(self, repositorio: PacienteRepository):
        self._repositorio = repositorio

    def crear(self, datos: PacienteCrear) -> Paciente:
        if self._repositorio.existe_por_rut(datos.rut):
            raise HTTPException(status.HTTP_409_CONFLICT, f"Ya existe un paciente con rut {datos.rut}")

        paciente = Paciente(**datos.model_dump())
        return self._repositorio.guardar(paciente)

    def obtener_por_id(self, id: int) -> Paciente:
        paciente = self._repositorio.buscar_por_id(id)
        if paciente is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Paciente {id} no encontrado")
        return paciente

    def listar_todos(self) -> list[Paciente]:
        return self._repositorio.listar_todos()

    def actualizar(self, id: int, datos: PacienteActualizar) -> Paciente:
        paciente = self.obtener_por_id(id)
        for campo, valor in datos.model_dump().items():
            setattr(paciente, campo, valor)
        return self._repositorio.guardar(paciente)

    def eliminar(self, id: int) -> None:
        paciente = self.obtener_por_id(id)
        self._repositorio.eliminar(paciente)
