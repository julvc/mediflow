import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from .bd import get_db
from .modelos import Usuario
from .seguridad import decodificar_token

_bearer = HTTPBearer(auto_error=False)


def get_usuario_actual(
    credenciales: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> Usuario:
    if credenciales is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Se requiere un token válido")

    try:
        payload = decodificar_token(credenciales.credentials)
    except jwt.PyJWTError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Se requiere un token válido") from exc

    usuario = db.get(Usuario, payload["usuarioId"])
    if usuario is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Se requiere un token válido")

    return usuario
