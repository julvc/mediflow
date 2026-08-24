from typing import Protocol

from sqlalchemy.orm import Session

from ..modelos import Profesional


class ProfesionalRepository(Protocol):
    def guardar(self, profesional: Profesional) -> Profesional: ...
    def buscar_por_id(self, id: int) -> Profesional | None: ...
    def listar_todos(self) -> list[Profesional]: ...
    def existe_por_rut(self, rut: str) -> bool: ...
    def eliminar(self, profesional: Profesional) -> None: ...


class SQLAlchemyProfesionalRepository:
    def __init__(self, db: Session):
        self._db = db

    def guardar(self, profesional: Profesional) -> Profesional:
        self._db.add(profesional)
        self._db.commit()
        self._db.refresh(profesional)
        return profesional

    def buscar_por_id(self, id: int) -> Profesional | None:
        return self._db.get(Profesional, id)

    def listar_todos(self) -> list[Profesional]:
        return list(self._db.query(Profesional).order_by(Profesional.id).all())

    def existe_por_rut(self, rut: str) -> bool:
        return self._db.query(Profesional).filter(Profesional.rut == rut).first() is not None

    def eliminar(self, profesional: Profesional) -> None:
        self._db.delete(profesional)
        self._db.commit()
