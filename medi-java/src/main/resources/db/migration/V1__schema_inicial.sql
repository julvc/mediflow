CREATE TABLE pacientes (
    id                BIGSERIAL PRIMARY KEY,
    rut               VARCHAR(12)  NOT NULL UNIQUE,
    nombres           VARCHAR(100) NOT NULL,
    apellidos         VARCHAR(100) NOT NULL,
    fecha_nacimiento  DATE         NOT NULL,
    email             VARCHAR(150) NOT NULL,
    telefono          VARCHAR(20),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE profesionales (
    id            BIGSERIAL PRIMARY KEY,
    rut           VARCHAR(12)  NOT NULL UNIQUE,
    nombres       VARCHAR(100) NOT NULL,
    apellidos     VARCHAR(100) NOT NULL,
    especialidad  VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE turnos (
    id             BIGSERIAL PRIMARY KEY,
    paciente_id    BIGINT       NOT NULL REFERENCES pacientes(id),
    profesional_id BIGINT       NOT NULL REFERENCES profesionales(id),
    fecha_hora     TIMESTAMPTZ  NOT NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                   CHECK (estado IN ('PENDIENTE', 'CONFIRMADO', 'CANCELADO', 'COMPLETADO')),
    motivo         VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Un profesional no puede tener dos turnos activos a la misma hora exacta.
CREATE UNIQUE INDEX uq_turno_profesional_fecha ON turnos (profesional_id, fecha_hora)
    WHERE estado <> 'CANCELADO';

CREATE INDEX ix_turnos_paciente_id ON turnos (paciente_id);
CREATE INDEX ix_turnos_profesional_id ON turnos (profesional_id);

CREATE TABLE documentos (
    id              BIGSERIAL PRIMARY KEY,
    turno_id        BIGINT       NOT NULL REFERENCES turnos(id),
    nombre_archivo  VARCHAR(255) NOT NULL,
    url_storage     VARCHAR(500) NOT NULL,
    tipo_documento  VARCHAR(50)  NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                    CHECK (estado IN ('PENDIENTE', 'PROCESADO', 'ERROR')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_documentos_turno_id ON documentos (turno_id);
