package com.mediflow.api.dto.documento;

import com.mediflow.api.domain.EstadoDocumento;

import java.time.Instant;

public record DocumentoResponse(
        Long id,
        Long turnoId,
        String nombreArchivo,
        String urlStorage,
        String tipoDocumento,
        EstadoDocumento estado,
        Instant creadoEn
) {
}
