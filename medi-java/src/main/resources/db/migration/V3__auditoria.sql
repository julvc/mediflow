-- Bitacora de acciones para control interno: quien hizo que, sobre que recurso,
-- cuando. Sin FK a usuarios a proposito: el registro debe sobrevivir aunque el
-- usuario que lo genero cambie o se elimine (no existe hoy DELETE /usuarios,
-- pero el log no deberia depender de esa integridad referencial de todos modos).
CREATE TABLE registro_auditoria (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT,
    usuario_email  VARCHAR(150) NOT NULL,
    accion         VARCHAR(30)  NOT NULL
        CHECK (accion IN ('CREAR', 'ACTUALIZAR', 'ELIMINAR', 'CAMBIAR_ESTADO', 'LOGIN', 'REGISTRO')),
    entidad        VARCHAR(30)  NOT NULL
        CHECK (entidad IN ('PACIENTE', 'PROFESIONAL', 'TURNO', 'DOCUMENTO', 'USUARIO')),
    entidad_id     BIGINT,
    detalle        VARCHAR(500),
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_registro_auditoria_creado_en ON registro_auditoria (creado_en DESC);
