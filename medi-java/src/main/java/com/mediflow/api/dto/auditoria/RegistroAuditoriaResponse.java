package com.mediflow.api.dto.auditoria;

import com.mediflow.api.domain.AccionAuditoria;
import com.mediflow.api.domain.EntidadAuditoria;

import java.time.Instant;

public record RegistroAuditoriaResponse(
        Long id,
        String usuarioEmail,
        AccionAuditoria accion,
        EntidadAuditoria entidad,
        Long entidadId,
        String detalle,
        Instant creadoEn
) {
}
