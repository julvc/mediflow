package com.mediflow.api.service.impl;

import com.mediflow.api.domain.AccionAuditoria;
import com.mediflow.api.domain.EntidadAuditoria;
import com.mediflow.api.domain.EstadoTurno;
import com.mediflow.api.domain.Paciente;
import com.mediflow.api.domain.Profesional;
import com.mediflow.api.domain.Rol;
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
import com.mediflow.api.security.UsuarioPrincipal;
import com.mediflow.api.service.AuditoriaService;
import com.mediflow.api.service.TurnoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final AuditoriaService auditoriaService;

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
        Turno guardado = turnoRepository.save(turno);
        auditoriaService.registrar(AccionAuditoria.CREAR, EntidadAuditoria.TURNO, guardado.getId(),
                "paciente " + guardado.getPaciente().getId() + ", profesional " + guardado.getProfesional().getId());
        return TurnoMapper.toResponse(guardado);
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
        validarPermisoDeTransicion(turno, nuevo);

        turno.setEstado(nuevo);
        Turno guardado = turnoRepository.save(turno);
        auditoriaService.registrar(AccionAuditoria.CAMBIAR_ESTADO, EntidadAuditoria.TURNO, guardado.getId(),
                actual + " -> " + nuevo);
        return TurnoMapper.toResponse(guardado);
    }

    // Un PACIENTE solo puede cancelar su propio turno; confirmarlo o completarlo son
    // decisiones del centro medico (PROFESIONAL/ADMIN), no del paciente. PROFESIONAL
    // y ADMIN mantienen via libre a cualquier transicion valida de TRANSICIONES_VALIDAS.
    private void validarPermisoDeTransicion(Turno turno, EstadoTurno nuevo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)
                || principal.getRol() != Rol.PACIENTE) {
            return;
        }
        if (nuevo != EstadoTurno.CANCELADO || !turno.getPaciente().getId().equals(principal.getPacienteId())) {
            throw new AccessDeniedException("Un paciente solo puede cancelar su propio turno");
        }
    }

    private Turno buscarOFallar(Long id) {
        return turnoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno " + id + " no encontrado"));
    }
}
