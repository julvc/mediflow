package com.mediflow.api.dto.profesional;

import java.time.Instant;

public record ProfesionalResponse(
        Long id,
        String rut,
        String nombres,
        String apellidos,
        String especialidad,
        String email,
        Instant creadoEn
) {
}
