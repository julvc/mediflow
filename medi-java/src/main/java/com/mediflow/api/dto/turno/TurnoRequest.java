package com.mediflow.api.dto.turno;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TurnoRequest(

        @NotNull(message = "el paciente es obligatorio")
        Long pacienteId,

        @NotNull(message = "el profesional es obligatorio")
        Long profesionalId,

        @NotNull(message = "la fecha y hora son obligatorias")
        @Future(message = "el turno debe agendarse a futuro")
        Instant fechaHora,

        @Size(max = 255, message = "el motivo no puede superar 255 caracteres")
        String motivo
) {
}
