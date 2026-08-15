package com.mediflow.api.dto.turno;

import com.mediflow.api.domain.EstadoTurno;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoTurnoRequest(
        @NotNull(message = "el nuevo estado es obligatorio")
        EstadoTurno estado
) {
}
