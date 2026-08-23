import re
from datetime import date, datetime

from pydantic import BaseModel, EmailStr, field_validator

_RUT_REGEX = re.compile(r"^[0-9]{7,8}-[0-9kK]$")


class PacienteCrear(BaseModel):
    rut: str
    nombres: str
    apellidos: str
    fecha_nacimiento: date
    email: EmailStr
    telefono: str | None = None

    @field_validator("rut")
    @classmethod
    def validar_rut(cls, v: str) -> str:
        if not _RUT_REGEX.match(v):
            raise ValueError("formato de rut inválido (ej: 12345678-9)")
        return v

    @field_validator("fecha_nacimiento")
    @classmethod
    def validar_fecha_pasado(cls, v: date) -> date:
        if v >= date.today():
            raise ValueError("la fecha de nacimiento debe ser en el pasado")
        return v

    @field_validator("nombres", "apellidos")
    @classmethod
    def validar_no_vacio(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("no puede estar vacío")
        return v


class PacienteActualizar(PacienteCrear):
    pass


class PacienteResponse(BaseModel):
    id: int
    rut: str
    nombres: str
    apellidos: str
    fecha_nacimiento: date
    email: str
    telefono: str | None
    creado_en: datetime

    model_config = {"from_attributes": True}


class RegistroRequest(BaseModel):
    email: EmailStr
    password: str

    @field_validator("password")
    @classmethod
    def validar_largo_password(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("la contraseña debe tener al menos 8 caracteres")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    expira_en_segundos: int
