from datetime import datetime
from typing import Protocol

from sqlalchemy.orm import Session

from ..dominio import EstadoTurno
from ..modelos import Turno


class TurnoRepository(Protocol):
    def guardar(self, turno: Turno) -> Turno: ...
    def buscar_por_id(self, id: int) -> Turno | None: ...
    def listar_todos(self) -> list[Turno]: ...
    def listar_por_paciente(self, paciente_id: int) -> list[Turno]: ...
    def existe_conflicto(self, profesional_id: int, fecha_hora: datetime) -> bool: ...


class SQLAlchemyTurnoRepository:
    def __init__(self, db: Session):
        self._db = db

    def guardar(self, turno: Turno) -> Turno:
        self._db.add(turno)
        self._db.commit()
        self._db.refresh(turno)
        return turno

    def buscar_por_id(self, id: int) -> Turno | None:
        return self._db.get(Turno, id)

    def listar_todos(self) -> list[Turno]:
        return list(self._db.query(Turno).order_by(Turno.id).all())

    def listar_por_paciente(self, paciente_id: int) -> list[Turno]:
        return list(self._db.query(Turno).filter(Turno.paciente_id == paciente_id).order_by(Turno.id).all())

    def existe_conflicto(self, profesional_id: int, fecha_hora: datetime) -> bool:
        return (
            self._db.query(Turno)
            .filter(
                Turno.profesional_id == profesional_id,
                Turno.fecha_hora == fecha_hora,
                Turno.estado != EstadoTurno.CANCELADO,
            )
            .first()
            is not None
        )
