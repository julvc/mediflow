package com.mediflow.api.mapper;

import com.mediflow.api.domain.Paciente;
import com.mediflow.api.dto.paciente.PacienteRequest;
import com.mediflow.api.dto.paciente.PacienteResponse;

/**
 * Traduce entre la entidad JPA y los DTOs de la API. Se separa del DTO
 * (que solo es forma de dato) porque el mapeo es comportamiento — SRP.
 * Métodos static: no tiene estado ni dependencias, no necesita ser un bean de Spring.
 */
public final class PacienteMapper {

    private PacienteMapper() {
    }

    public static Paciente toEntity(PacienteRequest request) {
        return Paciente.builder()
                .rut(request.rut())
                .nombres(request.nombres())
                .apellidos(request.apellidos())
                .fechaNacimiento(request.fechaNacimiento())
                .email(request.email())
                .telefono(request.telefono())
                .build();
    }

    public static PacienteResponse toResponse(Paciente entity) {
        return new PacienteResponse(
                entity.getId(),
                entity.getRut(),
                entity.getNombres(),
                entity.getApellidos(),
                entity.getFechaNacimiento(),
                entity.getEmail(),
                entity.getTelefono(),
                entity.getCreatedAt()
        );
    }

    /**
     * Aplica los campos editables sobre una entidad ya gestionada por JPA
     * (en vez de reconstruirla) para no perder su id ni sus timestamps de auditoría.
     */
    public static void actualizarEntidad(Paciente destino, PacienteRequest request) {
        destino.setRut(request.rut());
        destino.setNombres(request.nombres());
        destino.setApellidos(request.apellidos());
        destino.setFechaNacimiento(request.fechaNacimiento());
        destino.setEmail(request.email());
        destino.setTelefono(request.telefono());
    }
}
