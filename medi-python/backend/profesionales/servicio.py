from fastapi import HTTPException, status

from ..esquemas import ProfesionalActualizar, ProfesionalCrear
from ..modelos import Profesional
from .repositorio import ProfesionalRepository


class ProfesionalService:
    def __init__(self, repositorio: ProfesionalRepository):
        self._repositorio = repositorio

    def crear(self, datos: ProfesionalCrear) -> Profesional:
        if self._repositorio.existe_por_rut(datos.rut):
            raise HTTPException(status.HTTP_409_CONFLICT, f"Ya existe un profesional con rut {datos.rut}")

        profesional = Profesional(**datos.model_dump())
        return self._repositorio.guardar(profesional)

    def obtener_por_id(self, id: int) -> Profesional:
        profesional = self._repositorio.buscar_por_id(id)
        if profesional is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Profesional {id} no encontrado")
        return profesional

    def listar_todos(self) -> list[Profesional]:
        return self._repositorio.listar_todos()

    def actualizar(self, id: int, datos: ProfesionalActualizar) -> Profesional:
        profesional = self.obtener_por_id(id)
        for campo, valor in datos.model_dump().items():
            setattr(profesional, campo, valor)
        return self._repositorio.guardar(profesional)

    def eliminar(self, id: int) -> None:
        profesional = self.obtener_por_id(id)
        self._repositorio.eliminar(profesional)
