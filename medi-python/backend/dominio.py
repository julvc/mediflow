"""Enums de dominio — mismo set de valores que EstadoTurno/EstadoDocumento en
medi-java, para que ambos backends modelen exactamente la misma maquina de
estados."""

import enum


class EstadoTurno(str, enum.Enum):
    PENDIENTE = "PENDIENTE"
    CONFIRMADO = "CONFIRMADO"
    CANCELADO = "CANCELADO"
    COMPLETADO = "COMPLETADO"


class EstadoDocumento(str, enum.Enum):
    PENDIENTE = "PENDIENTE"
    PROCESADO = "PROCESADO"
    ERROR = "ERROR"


class Rol(str, enum.Enum):
    PACIENTE = "PACIENTE"
    PROFESIONAL = "PROFESIONAL"
    ADMIN = "ADMIN"


STAFF = (Rol.PROFESIONAL, Rol.ADMIN)
