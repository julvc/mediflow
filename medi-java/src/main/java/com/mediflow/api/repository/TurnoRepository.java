package com.mediflow.api.repository;

import com.mediflow.api.domain.EstadoTurno;
import com.mediflow.api.domain.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    List<Turno> findByPacienteId(Long pacienteId);

    boolean existsByProfesionalIdAndFechaHoraAndEstadoNot(
            Long profesionalId, Instant fechaHora, EstadoTurno estadoExcluido);
}
