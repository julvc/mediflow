package com.mediflow.api.dto.paciente;

import java.time.Instant;
import java.time.LocalDate;

public record PacienteResponse(
        Long id,
        String rut,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String email,
        String telefono,
        Instant creadoEn
) {
}
