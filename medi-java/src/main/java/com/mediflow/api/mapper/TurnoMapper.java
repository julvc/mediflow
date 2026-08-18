package com.mediflow.api.mapper;

import com.mediflow.api.domain.Paciente;
import com.mediflow.api.domain.Profesional;
import com.mediflow.api.domain.Turno;
import com.mediflow.api.dto.turno.TurnoRequest;
import com.mediflow.api.dto.turno.TurnoResponse;

public final class TurnoMapper {

    private TurnoMapper() {
    }

    /**
     * Recibe las entidades Paciente/Profesional ya resueltas por el service
     * (el mapper no consulta repositorios — esa responsabilidad es del service).
     */
    public static Turno toEntity(TurnoRequest request, Paciente paciente, Profesional profesional) {
        return Turno.builder()
                .paciente(paciente)
                .profesional(profesional)
                .fechaHora(request.fechaHora())
                .motivo(request.motivo())
                .build();
    }

    // El nombre completo se arma acá y no en la entidad: Turno es un modelo de
    // persistencia, no debería saber cómo se presenta un nombre en la API.
    public static TurnoResponse toResponse(Turno entity) {
        return new TurnoResponse(
                entity.getId(),
                entity.getPaciente().getId(),
                entity.getPaciente().getNombres() + " " + entity.getPaciente().getApellidos(),
                entity.getProfesional().getId(),
                entity.getProfesional().getNombres() + " " + entity.getProfesional().getApellidos(),
                entity.getFechaHora(),
                entity.getEstado(),
                entity.getMotivo(),
                entity.getCreatedAt()
        );
    }
}
