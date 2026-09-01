import hashlib
import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from .configuracion import configuracion
from .dominio import Rol

_ALGORITMO = "HS256"


def hashear_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verificar_password(password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))


def crear_access_token(
    usuario_id: int, email: str, rol: Rol, paciente_id: int | None, profesional_id: int | None
) -> tuple[str, int]:
    """El JWT es la fuente de identidad — mismos claims que JwtService en Java
    (sub, usuarioId, rol, pacienteId, profesionalId), asi que cualquier cliente
    (incluido el frontend React) decodifica el mismo shape sin importar contra
    cual backend hizo login."""
    expira_en_segundos = configuracion.jwt_expira_minutos * 60
    expira_en = datetime.now(timezone.utc) + timedelta(seconds=expira_en_segundos)
    payload = {
        "sub": email,
        "usuarioId": usuario_id,
        "rol": rol.value,
        "pacienteId": paciente_id,
        "profesionalId": profesional_id,
        "exp": expira_en,
    }
    token = jwt.encode(payload, configuracion.jwt_secret, algorithm=_ALGORITMO)
    return token, expira_en_segundos


def decodificar_token(token: str) -> dict:
    """Lanza jwt.PyJWTError (o subclases: ExpiredSignatureError, InvalidTokenError)
    si el token es invalido o expiro — la capa de dependencias lo traduce a 401."""
    return jwt.decode(token, configuracion.jwt_secret, algorithms=[_ALGORITMO])


def generar_refresh_token() -> tuple[str, str, datetime]:
    """Devuelve (token_plano, hash, expira_en). Solo el hash se guarda en la
    base — mismo patron que RefreshToken en Java (nunca se persiste el token
    en texto plano, para que un dump de la base no alcance para robar sesiones)."""
    token_plano = str(uuid.uuid4())
    token_hash = hashlib.sha256(token_plano.encode("utf-8")).hexdigest()
    expira_en = datetime.now(timezone.utc) + timedelta(days=configuracion.refresh_token_dias)
    return token_plano, token_hash, expira_en


def hashear_refresh_token(token_plano: str) -> str:
    return hashlib.sha256(token_plano.encode("utf-8")).hexdigest()
