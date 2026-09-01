from typing import Protocol

from sqlalchemy.orm import Session

from ..modelos import Documento


class DocumentoRepository(Protocol):
    def guardar(self, documento: Documento) -> Documento: ...
    def buscar_por_id(self, id: int) -> Documento | None: ...
    def listar_por_turno(self, turno_id: int) -> list[Documento]: ...
    def eliminar(self, documento: Documento) -> None: ...


class SQLAlchemyDocumentoRepository:
    def __init__(self, db: Session):
        self._db = db

    def guardar(self, documento: Documento) -> Documento:
        self._db.add(documento)
        self._db.commit()
        self._db.refresh(documento)
        return documento

    def buscar_por_id(self, id: int) -> Documento | None:
        return self._db.get(Documento, id)

    def listar_por_turno(self, turno_id: int) -> list[Documento]:
        return list(self._db.query(Documento).filter(Documento.turno_id == turno_id).order_by(Documento.id).all())

    def eliminar(self, documento: Documento) -> None:
        self._db.delete(documento)
        self._db.commit()
