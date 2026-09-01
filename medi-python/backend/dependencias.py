from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .dominio import Rol
from .seguridad import decodificar_token

_bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class UsuarioActual:
    """Equivalente a UsuarioPrincipal en Java: los datos de autorizacion
    salen directo de los claims del JWT, sin ida a la base en cada request —
    el token ya es la fuente de verdad una vez firmado en el login."""

    usuario_id: int
    email: str
    rol: Rol
    paciente_id: int | None
    profesional_id: int | None


def _decodificar_o_401(credenciales: HTTPAuthorizationCredentials) -> UsuarioActual:
    try:
        payload = decodificar_token(credenciales.credentials)
    except jwt.PyJWTError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Se requiere un token válido") from exc

    return UsuarioActual(
        usuario_id=payload["usuarioId"],
        email=payload["sub"],
        rol=Rol(payload["rol"]),
        paciente_id=payload.get("pacienteId"),
        profesional_id=payload.get("profesionalId"),
    )


def get_usuario_actual(credenciales: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> UsuarioActual:
    if credenciales is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Se requiere un token válido")
    return _decodificar_o_401(credenciales)


def get_usuario_actual_opcional(
    credenciales: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> UsuarioActual | None:
    """Para /auth/registro: el registro publico de PACIENTE no exige sesion,
    pero registrar PROFESIONAL/ADMIN si — este dependency deja pasar la
    llamada anonima en vez de rechazarla con 401, y el router decide."""
    if credenciales is None:
        return None
    return _decodificar_o_401(credenciales)


def requiere_roles(*roles: Rol):
    """Equivalente a @PreAuthorize("hasAnyRole(...)"): factory de dependencia
    que 403-ea si el rol del usuario autenticado no esta en la lista."""

    def _verificar(usuario: UsuarioActual = Depends(get_usuario_actual)) -> UsuarioActual:
        if usuario.rol not in roles:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permiso para esta operación")
        return usuario

    return _verificar
