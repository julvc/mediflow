from datetime import datetime, timezone

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from ..dependencias import UsuarioActual
from ..dominio import Rol
from ..esquemas import LoginRequest, RegistroRequest
from ..modelos import RefreshToken, Usuario
from ..pacientes.repositorio import PacienteRepository
from ..profesionales.repositorio import ProfesionalRepository
from ..seguridad import (
    crear_access_token,
    generar_refresh_token,
    hashear_password,
    hashear_refresh_token,
    verificar_password,
)


class AuthService:
    def __init__(self, db: Session, pacientes: PacienteRepository, profesionales: ProfesionalRepository):
        self._db = db
        self._pacientes = pacientes
        self._profesionales = profesionales

    def registrar(self, datos: RegistroRequest, solicitante: UsuarioActual | None) -> Usuario:
        # El registro publico (sin token) solo puede crear cuentas PACIENTE. Crear
        # PROFESIONAL o ADMIN exige ya estar autenticado como ADMIN — mismo chequeo
        # que AuthServiceImpl en Java, mismo motivo: sin esto cualquier anonimo
        # podria auto-otorgarse ADMIN llamando este mismo endpoint publico.
        if datos.rol != Rol.PACIENTE and (solicitante is None or solicitante.rol != Rol.ADMIN):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Solo un administrador puede registrar cuentas de PROFESIONAL o ADMIN")

        if self._db.query(Usuario).filter(Usuario.email == datos.email).first() is not None:
            raise HTTPException(status.HTTP_409_CONFLICT, f"Ya existe un usuario con el email {datos.email}")

        self._validar_vinculo_de_dominio(datos)

        usuario = Usuario(
            email=datos.email,
            password_hash=hashear_password(datos.password),
            rol=datos.rol,
            paciente_id=datos.paciente_id,
            profesional_id=datos.profesional_id,
        )
        self._db.add(usuario)
        self._db.commit()
        self._db.refresh(usuario)
        return usuario

    def _validar_vinculo_de_dominio(self, datos: RegistroRequest) -> None:
        if datos.rol == Rol.PACIENTE:
            # El email del registro debe coincidir con el de la ficha del paciente:
            # sin este chequeo, cualquier anonimo con el pacienteId de OTRA persona
            # podria vincularse a esa ficha y tomar control de ella (IDOR). Mismo
            # mensaje que "no encontrado" para no revelar que el paciente existe
            # pero el email no coincide (evita un oraculo de enumeracion).
            paciente = self._pacientes.buscar_por_id(datos.paciente_id) if datos.paciente_id else None
            if paciente is None or paciente.email.lower() != datos.email.lower():
                raise HTTPException(status.HTTP_404_NOT_FOUND, f"Paciente {datos.paciente_id} no encontrado")
        elif datos.rol == Rol.PROFESIONAL:
            if datos.profesional_id is None or self._profesionales.buscar_por_id(datos.profesional_id) is None:
                raise HTTPException(status.HTTP_404_NOT_FOUND, f"Profesional {datos.profesional_id} no encontrado")

    def login(self, datos: LoginRequest) -> tuple[str, str, int]:
        usuario = self._db.query(Usuario).filter(Usuario.email == datos.email).first()
        if usuario is None or not verificar_password(datos.password, usuario.password_hash):
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Email o contraseña incorrectos")

        return self._generar_tokens(usuario)

    def refrescar(self, refresh_token_plano: str) -> tuple[str, str, int]:
        token_hash = hashear_refresh_token(refresh_token_plano)
        guardado = self._db.query(RefreshToken).filter(
            RefreshToken.token_hash == token_hash, RefreshToken.revocado.is_(False)
        ).first()
        if guardado is None:
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Refresh token inválido o revocado")
        # SQLite no preserva tzinfo en DateTime(timezone=True) — vuelve naive
        # al leerlo. Postgres si lo preserva. Se normaliza a UTC aware en
        # ambos lados para que la comparacion funcione igual en los dos.
        expira_en = guardado.expira_en
        if expira_en.tzinfo is None:
            expira_en = expira_en.replace(tzinfo=timezone.utc)
        if expira_en < datetime.now(timezone.utc):
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Refresh token expirado")

        usuario = self._db.get(Usuario, guardado.usuario_id)
        if usuario is None:
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Refresh token inválido o revocado")

        # Rotacion: el refresh token usado se revoca y se emite uno nuevo. Si
        # alguien reutiliza un refresh token ya canjeado, ese segundo intento
        # falla (ya revocado) — mismo mecanismo que RefreshToken en Java.
        guardado.revocado = True
        self._db.commit()

        return self._generar_tokens(usuario)

    def logout(self, refresh_token_plano: str) -> None:
        token_hash = hashear_refresh_token(refresh_token_plano)
        guardado = self._db.query(RefreshToken).filter(
            RefreshToken.token_hash == token_hash, RefreshToken.revocado.is_(False)
        ).first()
        if guardado is not None:
            guardado.revocado = True
            self._db.commit()

    def _generar_tokens(self, usuario: Usuario) -> tuple[str, str, int]:
        access_token, expira_en_segundos = crear_access_token(
            usuario.id, usuario.email, usuario.rol, usuario.paciente_id, usuario.profesional_id
        )
        refresh_plano, refresh_hash, refresh_expira_en = generar_refresh_token()
        self._db.add(RefreshToken(usuario_id=usuario.id, token_hash=refresh_hash, expira_en=refresh_expira_en))
        self._db.commit()
        return access_token, refresh_plano, expira_en_segundos
