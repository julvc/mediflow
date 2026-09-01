from fastapi import HTTPException, status

from ..dominio import EstadoDocumento
from ..esquemas import DocumentoCrear
from ..modelos import Documento
from ..turnos.repositorio import TurnoRepository
from .repositorio import DocumentoRepository

# Ambos terminales: el worker no reintenta un documento fallido, sube uno
# nuevo — mismo razonamiento que DocumentoServiceImpl (Java).
TRANSICIONES_VALIDAS: dict[EstadoDocumento, set[EstadoDocumento]] = {
    EstadoDocumento.PENDIENTE: {EstadoDocumento.PROCESADO, EstadoDocumento.ERROR},
    EstadoDocumento.PROCESADO: set(),
    EstadoDocumento.ERROR: set(),
}


class DocumentoService:
    def __init__(self, repositorio: DocumentoRepository, turnos: TurnoRepository):
        self._repositorio = repositorio
        self._turnos = turnos

    def crear(self, datos: DocumentoCrear) -> Documento:
        if self._turnos.buscar_por_id(datos.turno_id) is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Turno {datos.turno_id} no encontrado")

        documento = Documento(**datos.model_dump(), estado=EstadoDocumento.PENDIENTE)
        return self._repositorio.guardar(documento)

    def obtener_por_id(self, id: int) -> Documento:
        documento = self._repositorio.buscar_por_id(id)
        if documento is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Documento {id} no encontrado")
        return documento

    def listar_por_turno(self, turno_id: int) -> list[Documento]:
        return self._repositorio.listar_por_turno(turno_id)

    def cambiar_estado(self, id: int, nuevo_estado: EstadoDocumento) -> Documento:
        documento = self.obtener_por_id(id)
        if nuevo_estado not in TRANSICIONES_VALIDAS[documento.estado]:
            raise HTTPException(
                status.HTTP_409_CONFLICT, f"No se puede pasar de {documento.estado.value} a {nuevo_estado.value}"
            )

        documento.estado = nuevo_estado
        return self._repositorio.guardar(documento)

    def eliminar(self, id: int) -> None:
        documento = self.obtener_por_id(id)
        self._repositorio.eliminar(documento)
