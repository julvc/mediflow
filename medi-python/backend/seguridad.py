from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from .configuracion import configuracion

_ALGORITMO = "HS256"


def hashear_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verificar_password(password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))


def crear_token(usuario_id: int, email: str) -> tuple[str, int]:
    expira_en_segundos = configuracion.jwt_expira_minutos * 60
    expira_en = datetime.now(timezone.utc) + timedelta(seconds=expira_en_segundos)
    payload = {"sub": email, "usuarioId": usuario_id, "exp": expira_en}
    token = jwt.encode(payload, configuracion.jwt_secret, algorithm=_ALGORITMO)
    return token, expira_en_segundos


def decodificar_token(token: str) -> dict:
    """Lanza jwt.PyJWTError (o subclases: ExpiredSignatureError, InvalidTokenError)
    si el token es invalido o expiro — la capa de dependencias lo traduce a 401."""
    return jwt.decode(token, configuracion.jwt_secret, algorithms=[_ALGORITMO])
