CREATE TABLE usuarios (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(150) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('PACIENTE', 'PROFESIONAL', 'ADMIN')),
    paciente_id    BIGINT REFERENCES pacientes(id),
    profesional_id BIGINT REFERENCES profesionales(id),
    activo         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_usuario_vinculo_segun_rol CHECK (
        (rol = 'PACIENTE'    AND paciente_id IS NOT NULL AND profesional_id IS NULL) OR
        (rol = 'PROFESIONAL' AND profesional_id IS NOT NULL AND paciente_id IS NULL) OR
        (rol = 'ADMIN'       AND paciente_id IS NULL AND profesional_id IS NULL)
    )
);

-- Un Paciente/Profesional no puede tener mas de un Usuario asociado (vinculo 1 a 1).
CREATE UNIQUE INDEX uq_usuario_paciente_id ON usuarios (paciente_id) WHERE paciente_id IS NOT NULL;
CREATE UNIQUE INDEX uq_usuario_profesional_id ON usuarios (profesional_id) WHERE profesional_id IS NOT NULL;

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios(id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ  NOT NULL,
    revocado    BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX ix_refresh_tokens_usuario_id ON refresh_tokens (usuario_id);

-- pgcrypto: solo se usa aca, una vez, para generar el hash BCrypt del ADMIN semilla
-- sin tener que pegar un hash calculado a mano (riesgo de copiar mal el string).
-- crypt()/gen_salt('bf') produce el mismo formato $2a$ que BCryptPasswordEncoder.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ADMIN de arranque: sin esto no hay forma de crear el primer ADMIN, porque
-- POST /api/v1/auth/registro con rol=ADMIN exige ya estar autenticado como ADMIN.
-- Password de desarrollo: 'admin1234' (cambiar credenciales reales en produccion).
INSERT INTO usuarios (email, password_hash, rol, activo)
VALUES ('admin@mediflow.cl', crypt('admin1234', gen_salt('bf', 10)), 'ADMIN', true);
