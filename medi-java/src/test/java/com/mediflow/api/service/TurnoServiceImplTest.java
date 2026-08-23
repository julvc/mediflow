package com.mediflow.api.service;

import com.mediflow.api.domain.EstadoTurno;
import com.mediflow.api.domain.Paciente;
import com.mediflow.api.domain.Profesional;
import com.mediflow.api.domain.Turno;
import com.mediflow.api.dto.turno.CambioEstadoTurnoRequest;
import com.mediflow.api.dto.turno.TurnoRequest;
import com.mediflow.api.dto.turno.TurnoResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.repository.TurnoRepository;
import com.mediflow.api.service.impl.TurnoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario "puro": sin Spring, sin base de datos — solo mockea los repositorios.
// Es rápido y aísla la regla de negocio de la infraestructura.
@ExtendWith(MockitoExtension.class)
class TurnoServiceImplTest {

    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private TurnoServiceImpl turnoService;

    private Paciente paciente;
    private Profesional profesional;
    private Instant fechaFutura;

    @BeforeEach
    void setUp() {
        paciente = Paciente.builder().id(1L).nombres("Ana").apellidos("Soto").build();
        profesional = Profesional.builder().id(2L).nombres("Luis").apellidos("Diaz").build();
        fechaFutura = Instant.now().plus(1, ChronoUnit.DAYS);
    }

    @Test
    void crear_conDatosValidos_devuelveTurnoPendiente() {
        TurnoRequest request = new TurnoRequest(1L, 2L, fechaFutura, "Control anual");

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(profesionalRepository.findById(2L)).thenReturn(Optional.of(profesional));
        when(turnoRepository.existsByProfesionalIdAndFechaHoraAndEstadoNot(2L, fechaFutura, EstadoTurno.CANCELADO))
                .thenReturn(false);
        when(turnoRepository.save(any(Turno.class))).thenAnswer(invocacion -> {
            Turno turno = invocacion.getArgument(0);
            turno.setId(10L);
            return turno;
        });

        TurnoResponse response = turnoService.crear(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.estado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(response.nombrePaciente()).isEqualTo("Ana Soto");
        assertThat(response.nombreProfesional()).isEqualTo("Luis Diaz");

        ArgumentCaptor<Turno> captor = ArgumentCaptor.forClass(Turno.class);
        verify(turnoRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void crear_conPacienteInexistente_lanzaResourceNotFound() {
        TurnoRequest request = new TurnoRequest(99L, 2L, fechaFutura, null);
        when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnoService.crear(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(turnoRepository, never()).save(any());
    }

    @Test
    void crear_conProfesionalInexistente_lanzaResourceNotFound() {
        TurnoRequest request = new TurnoRequest(1L, 99L, fechaFutura, null);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(profesionalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnoService.crear(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(turnoRepository, never()).save(any());
    }

    @Test
    void crear_conProfesionalYaOcupadoAEsaHora_lanzaConflicto() {
        TurnoRequest request = new TurnoRequest(1L, 2L, fechaFutura, null);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(profesionalRepository.findById(2L)).thenReturn(Optional.of(profesional));
        when(turnoRepository.existsByProfesionalIdAndFechaHoraAndEstadoNot(2L, fechaFutura, EstadoTurno.CANCELADO))
                .thenReturn(true);

        assertThatThrownBy(() -> turnoService.crear(request))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(turnoRepository, never()).save(any());
    }

    @Test
    void cambiarEstado_deCanceladoAConfirmado_lanzaConflicto() {
        Turno turnoCancelado = Turno.builder()
                .id(5L).paciente(paciente).profesional(profesional)
                .fechaHora(fechaFutura).estado(EstadoTurno.CANCELADO).build();
        when(turnoRepository.findById(5L)).thenReturn(Optional.of(turnoCancelado));

        assertThatThrownBy(() -> turnoService.cambiarEstado(5L, new CambioEstadoTurnoRequest(EstadoTurno.CONFIRMADO)))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(turnoRepository, never()).save(any());
    }

    @Test
    void obtenerPorId_conIdInexistente_lanzaResourceNotFound() {
        when(turnoRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnoService.obtenerPorId(123L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
