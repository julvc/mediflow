package com.mediflow.api.dto.documento;

import com.mediflow.api.domain.EstadoDocumento;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoDocumentoRequest(
        @NotNull(message = "el nuevo estado es obligatorio")
        EstadoDocumento estado
) {
}
