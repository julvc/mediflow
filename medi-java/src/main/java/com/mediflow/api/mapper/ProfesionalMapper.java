package com.mediflow.api.mapper;

import com.mediflow.api.domain.Profesional;
import com.mediflow.api.dto.profesional.ProfesionalRequest;
import com.mediflow.api.dto.profesional.ProfesionalResponse;

public final class ProfesionalMapper {

    private ProfesionalMapper() {
    }

    public static Profesional toEntity(ProfesionalRequest request) {
        return Profesional.builder()
                .rut(request.rut())
                .nombres(request.nombres())
                .apellidos(request.apellidos())
                .especialidad(request.especialidad())
                .email(request.email())
                .build();
    }

    public static ProfesionalResponse toResponse(Profesional entity) {
        return new ProfesionalResponse(
                entity.getId(),
                entity.getRut(),
                entity.getNombres(),
                entity.getApellidos(),
                entity.getEspecialidad(),
                entity.getEmail()
        );
    }

    public static void actualizarEntidad(Profesional destino, ProfesionalRequest request) {
        destino.setRut(request.rut());
        destino.setNombres(request.nombres());
        destino.setApellidos(request.apellidos());
        destino.setEspecialidad(request.especialidad());
        destino.setEmail(request.email());
    }
}
