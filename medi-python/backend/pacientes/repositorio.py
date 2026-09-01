from typing import Protocol

from sqlalchemy.orm import Session

from ..modelos import Paciente


class PacienteRepository(Protocol):
    """Abstraccion (DIP): el servicio depende de esto, no de SQLAlchemy."""

    def guardar(self, paciente: Paciente) -> Paciente: ...
    def buscar_por_id(self, id: int) -> Paciente | None: ...
    def listar_todos(self) -> list[Paciente]: ...
    def existe_por_rut(self, rut: str) -> bool: ...
    def eliminar(self, paciente: Paciente) -> None: ...


class SQLAlchemyPacienteRepository:
    """Unica implementacion hoy — si en el futuro se necesita otra fuente de
    datos (ej: cache, otra BD), se agrega una clase nueva sin tocar el
    servicio (OCP)."""

    def __init__(self, db: Session):
        self._db = db

    def guardar(self, paciente: Paciente) -> Paciente:
        self._db.add(paciente)
        self._db.commit()
        self._db.refresh(paciente)
        return paciente

    def buscar_por_id(self, id: int) -> Paciente | None:
        return self._db.get(Paciente, id)

    def listar_todos(self) -> list[Paciente]:
        return list(self._db.query(Paciente).order_by(Paciente.id).all())

    def existe_por_rut(self, rut: str) -> bool:
        return self._db.query(Paciente).filter(Paciente.rut == rut).first() is not None

    def eliminar(self, paciente: Paciente) -> None:
        self._db.delete(paciente)
        self._db.commit()
