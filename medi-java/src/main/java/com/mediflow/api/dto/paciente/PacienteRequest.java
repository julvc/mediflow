package com.mediflow.api.dto.paciente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record PacienteRequest(

        @NotBlank(message = "el rut es obligatorio")
        @Pattern(regexp = "^[0-9]{7,8}-[0-9kK]$", message = "formato de rut inválido (ej: 12345678-9)")
        String rut,

        @NotBlank(message = "los nombres son obligatorios")
        String nombres,

        @NotBlank(message = "los apellidos son obligatorios")
        String apellidos,

        @NotNull(message = "la fecha de nacimiento es obligatoria")
        @Past(message = "la fecha de nacimiento debe ser en el pasado")
        LocalDate fechaNacimiento,

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato válido")
        String email,

        String telefono
) {
}
