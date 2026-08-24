import re
from datetime import date, datetime

from pydantic import BaseModel, EmailStr, field_validator

from .dominio import EstadoDocumento, EstadoTurno, Rol

_RUT_REGEX = re.compile(r"^[0-9]{7,8}-[0-9kK]$")


def _validar_formato_rut(v: str) -> str:
    if not _RUT_REGEX.match(v):
        raise ValueError("formato de rut inválido (ej: 12345678-9)")
    return v


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
        return _validar_formato_rut(v)

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


class ProfesionalCrear(BaseModel):
    rut: str
    nombres: str
    apellidos: str
    especialidad: str
    email: EmailStr

    @field_validator("rut")
    @classmethod
    def validar_rut(cls, v: str) -> str:
        return _validar_formato_rut(v)


class ProfesionalActualizar(ProfesionalCrear):
    pass


class ProfesionalResponse(BaseModel):
    id: int
    rut: str
    nombres: str
    apellidos: str
    especialidad: str
    email: str
    creado_en: datetime

    model_config = {"from_attributes": True}


class TurnoCrear(BaseModel):
    paciente_id: int
    profesional_id: int
    fecha_hora: datetime
    motivo: str | None = None

    @field_validator("fecha_hora")
    @classmethod
    def validar_fecha_futura(cls, v: datetime) -> datetime:
        ahora = datetime.now(v.tzinfo) if v.tzinfo else datetime.now()
        if v <= ahora:
            raise ValueError("el turno debe agendarse a futuro")
        return v


class CambioEstadoTurno(BaseModel):
    estado: EstadoTurno


class TurnoResponse(BaseModel):
    id: int
    paciente_id: int
    profesional_id: int
    fecha_hora: datetime
    estado: EstadoTurno
    motivo: str | None
    creado_en: datetime

    model_config = {"from_attributes": True}


class DocumentoCrear(BaseModel):
    turno_id: int
    nombre_archivo: str
    url_storage: str
    tipo_documento: str


class CambioEstadoDocumento(BaseModel):
    estado: EstadoDocumento


class DocumentoResponse(BaseModel):
    id: int
    turno_id: int
    nombre_archivo: str
    url_storage: str
    tipo_documento: str
    estado: EstadoDocumento
    creado_en: datetime

    model_config = {"from_attributes": True}


class RegistroRequest(BaseModel):
    email: EmailStr
    password: str
    rol: Rol = Rol.PACIENTE
    paciente_id: int | None = None
    profesional_id: int | None = None

    @field_validator("password")
    @classmethod
    def validar_largo_password(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("la contraseña debe tener al menos 8 caracteres")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class RefreshRequest(BaseModel):
    refresh_token: str


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    expira_en_segundos: int
