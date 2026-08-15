package com.mediflow.api.dto.documento;

import com.mediflow.api.domain.EstadoDocumento;

public record DocumentoResponse(
        Long id,
        Long turnoId,
        String nombreArchivo,
        String urlStorage,
        String tipoDocumento,
        EstadoDocumento estado
) {
}
