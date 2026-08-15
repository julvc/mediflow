package com.mediflow.api.dto.turno;

import com.mediflow.api.domain.EstadoTurno;

import java.time.Instant;

public record TurnoResponse(
        Long id,
        Long pacienteId,
        String nombrePaciente,
        Long profesionalId,
        String nombreProfesional,
        Instant fechaHora,
        EstadoTurno estado,
        String motivo
) {
}
