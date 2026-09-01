package com.mediflow.api.dto.profesional;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProfesionalRequest(

        @NotBlank(message = "el rut es obligatorio")
        @Pattern(regexp = "^[0-9]{7,8}-[0-9kK]$", message = "formato de rut inválido (ej: 12345678-9)")
        String rut,

        @NotBlank(message = "los nombres son obligatorios")
        String nombres,

        @NotBlank(message = "los apellidos son obligatorios")
        String apellidos,

        @NotBlank(message = "la especialidad es obligatoria")
        String especialidad,

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato válido")
        String email
) {
}
