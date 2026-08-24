from fastapi import HTTPException, status

from ..dependencias import UsuarioActual
from ..dominio import EstadoTurno, Rol
from ..esquemas import TurnoCrear
from ..modelos import Turno
from ..pacientes.repositorio import PacienteRepository
from ..profesionales.repositorio import ProfesionalRepository
from .repositorio import TurnoRepository

# Maquina de estados minima — mismo mapa que TurnoServiceImpl (Java), como
# dato en vez de una jerarquia de clases por estado (4 valores fijos no
# justifican ese sobre-diseño en ninguno de los dos stacks).
TRANSICIONES_VALIDAS: dict[EstadoTurno, set[EstadoTurno]] = {
    EstadoTurno.PENDIENTE: {EstadoTurno.CONFIRMADO, EstadoTurno.CANCELADO},
    EstadoTurno.CONFIRMADO: {EstadoTurno.COMPLETADO, EstadoTurno.CANCELADO},
    EstadoTurno.CANCELADO: set(),
    EstadoTurno.COMPLETADO: set(),
}


class TurnoService:
    def __init__(
        self,
        repositorio: TurnoRepository,
        pacientes: PacienteRepository,
        profesionales: ProfesionalRepository,
    ):
        self._repositorio = repositorio
        self._pacientes = pacientes
        self._profesionales = profesionales

    def crear(self, datos: TurnoCrear) -> Turno:
        if self._pacientes.buscar_por_id(datos.paciente_id) is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Paciente {datos.paciente_id} no encontrado")
        if self._profesionales.buscar_por_id(datos.profesional_id) is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Profesional {datos.profesional_id} no encontrado")
        if self._repositorio.existe_conflicto(datos.profesional_id, datos.fecha_hora):
            raise HTTPException(
                status.HTTP_409_CONFLICT,
                f"El profesional {datos.profesional_id} ya tiene un turno a esa hora",
            )

        turno = Turno(**datos.model_dump(), estado=EstadoTurno.PENDIENTE)
        return self._repositorio.guardar(turno)

    def obtener_por_id(self, id: int) -> Turno:
        turno = self._repositorio.buscar_por_id(id)
        if turno is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, f"Turno {id} no encontrado")
        return turno

    def listar_todos(self) -> list[Turno]:
        return self._repositorio.listar_todos()

    def listar_por_paciente(self, paciente_id: int) -> list[Turno]:
        return self._repositorio.listar_por_paciente(paciente_id)

    def cambiar_estado(self, id: int, nuevo_estado: EstadoTurno, usuario: UsuarioActual) -> Turno:
        turno = self.obtener_por_id(id)
        if nuevo_estado not in TRANSICIONES_VALIDAS[turno.estado]:
            raise HTTPException(
                status.HTTP_409_CONFLICT, f"No se puede pasar de {turno.estado.value} a {nuevo_estado.value}"
            )
        self._validar_permiso_de_transicion(turno, nuevo_estado, usuario)

        turno.estado = nuevo_estado
        return self._repositorio.guardar(turno)

    def _validar_permiso_de_transicion(self, turno: Turno, nuevo_estado: EstadoTurno, usuario: UsuarioActual) -> None:
        # Un PACIENTE solo puede cancelar su propio turno; confirmarlo o
        # completarlo son decisiones del centro medico — mismo chequeo que
        # TurnoServiceImpl en Java, mismo motivo.
        if usuario.rol != Rol.PACIENTE:
            return
        if nuevo_estado != EstadoTurno.CANCELADO or turno.paciente_id != usuario.paciente_id:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Un paciente solo puede cancelar su propio turno")
