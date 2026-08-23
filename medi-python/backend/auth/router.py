"""Auth minima para este backend: registro + login con JWT propio.

A diferencia de medi-java, no hay refresh token con rotacion ni roles — es
el subconjunto necesario para demostrar el patron (hash de password, emision
y validacion de JWT) sin duplicar semanas de trabajo de auth de Java. El
patron (hash -> verificar -> emitir token) es el mismo en ambos stacks.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..bd import get_db
from ..esquemas import LoginRequest, RegistroRequest, TokenResponse
from ..modelos import Usuario
from ..seguridad import crear_token, hashear_password, verificar_password

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/registro", status_code=status.HTTP_201_CREATED)
def registro(datos: RegistroRequest, db: Session = Depends(get_db)):
    ya_existe = db.query(Usuario).filter(Usuario.email == datos.email).first() is not None
    if ya_existe:
        raise HTTPException(status.HTTP_409_CONFLICT, f"Ya existe un usuario con el email {datos.email}")

    usuario = Usuario(email=datos.email, password_hash=hashear_password(datos.password))
    db.add(usuario)
    db.commit()
    db.refresh(usuario)
    return {"id": usuario.id, "email": usuario.email}


@router.post("/login", response_model=TokenResponse)
def login(datos: LoginRequest, db: Session = Depends(get_db)):
    usuario = db.query(Usuario).filter(Usuario.email == datos.email).first()
    if usuario is None or not verificar_password(datos.password, usuario.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Email o contraseña incorrectos")

    token, expira_en_segundos = crear_token(usuario.id, usuario.email)
    return TokenResponse(access_token=token, expira_en_segundos=expira_en_segundos)
