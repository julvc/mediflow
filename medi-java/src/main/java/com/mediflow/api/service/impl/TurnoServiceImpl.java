package com.mediflow.api.service.impl;

import com.mediflow.api.domain.EstadoTurno;
import com.mediflow.api.domain.Paciente;
import com.mediflow.api.domain.Profesional;
import com.mediflow.api.domain.Turno;
import com.mediflow.api.dto.turno.CambioEstadoTurnoRequest;
import com.mediflow.api.dto.turno.TurnoRequest;
import com.mediflow.api.dto.turno.TurnoResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.mapper.TurnoMapper;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.repository.TurnoRepository;
import com.mediflow.api.service.TurnoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TurnoServiceImpl implements TurnoService {

    // Máquina de estados mínima: qué transiciones son válidas desde cada estado.
    // Se modela como dato (Map) en vez de un patrón State: para 4 valores fijos,
    // un patrón de clases por estado sería sobre-diseño.
    private static final Map<EstadoTurno, Set<EstadoTurno>> TRANSICIONES_VALIDAS = new EnumMap<>(Map.of(
            EstadoTurno.PENDIENTE, EnumSet.of(EstadoTurno.CONFIRMADO, EstadoTurno.CANCELADO),
            EstadoTurno.CONFIRMADO, EnumSet.of(EstadoTurno.COMPLETADO, EstadoTurno.CANCELADO),
            EstadoTurno.CANCELADO, EnumSet.noneOf(EstadoTurno.class),
            EstadoTurno.COMPLETADO, EnumSet.noneOf(EstadoTurno.class)
    ));

    private final TurnoRepository turnoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;

    @Override
    @Transactional
    public TurnoResponse crear(TurnoRequest request) {
        Paciente paciente = pacienteRepository.findById(request.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente " + request.pacienteId() + " no encontrado"));
        Profesional profesional = profesionalRepository.findById(request.profesionalId())
                .orElseThrow(() -> new ResourceNotFoundException("Profesional " + request.profesionalId() + " no encontrado"));

        boolean profesionalOcupado = turnoRepository.existsByProfesionalIdAndFechaHoraAndEstadoNot(
                request.profesionalId(), request.fechaHora(), EstadoTurno.CANCELADO);
        if (profesionalOcupado) {
            throw new ConflictoDeNegocioException(
                    "El profesional " + request.profesionalId() + " ya tiene un turno a esa hora");
        }

        Turno turno = TurnoMapper.toEntity(request, paciente, profesional);
        turno.setEstado(EstadoTurno.PENDIENTE);
        return TurnoMapper.toResponse(turnoRepository.save(turno));
    }

    @Override
    @Transactional(readOnly = true)
    public TurnoResponse obtenerPorId(Long id) {
        return TurnoMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoResponse> listarTodos() {
        return turnoRepository.findAll().stream().map(TurnoMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoResponse> listarPorPaciente(Long pacienteId) {
        return turnoRepository.findByPacienteId(pacienteId).stream().map(TurnoMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public TurnoResponse cambiarEstado(Long id, CambioEstadoTurnoRequest request) {
        Turno turno = buscarOFallar(id);
        EstadoTurno actual = turno.getEstado();
        EstadoTurno nuevo = request.estado();

        if (!TRANSICIONES_VALIDAS.get(actual).contains(nuevo)) {
            throw new ConflictoDeNegocioException("No se puede pasar de " + actual + " a " + nuevo);
        }

        turno.setEstado(nuevo);
        return TurnoMapper.toResponse(turnoRepository.save(turno));
    }

    private Turno buscarOFallar(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno " + id + " no encontrado"));
    }
}
