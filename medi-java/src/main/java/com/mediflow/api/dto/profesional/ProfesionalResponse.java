package com.mediflow.api.dto.profesional;

public record ProfesionalResponse(
        Long id,
        String rut,
        String nombres,
        String apellidos,
        String especialidad,
        String email
) {
}
