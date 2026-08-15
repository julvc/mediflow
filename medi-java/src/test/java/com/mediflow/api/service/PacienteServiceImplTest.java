package com.mediflow.api.service;

import com.mediflow.api.domain.Paciente;
import com.mediflow.api.dto.paciente.PacienteRequest;
import com.mediflow.api.dto.paciente.PacienteResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.service.impl.PacienteServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteServiceImplTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private PacienteServiceImpl pacienteService;

    @Test
    void crear_conRutNuevo_guardaYDevuelveResponse() {
        PacienteRequest request = new PacienteRequest(
                "12345678-9", "Ana", "Soto", LocalDate.of(1990, 1, 1), "ana@test.cl", "912345678");
        when(pacienteRepository.existsByRut("12345678-9")).thenReturn(false);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocacion -> {
            Paciente paciente = invocacion.getArgument(0);
            paciente.setId(1L);
            return paciente;
        });

        PacienteResponse response = pacienteService.crear(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.rut()).isEqualTo("12345678-9");
    }

    @Test
    void crear_conRutYaRegistrado_lanzaConflicto() {
        PacienteRequest request = new PacienteRequest(
                "12345678-9", "Ana", "Soto", LocalDate.of(1990, 1, 1), "ana@test.cl", null);
        when(pacienteRepository.existsByRut("12345678-9")).thenReturn(true);

        assertThatThrownBy(() -> pacienteService.crear(request))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void obtenerPorId_conIdInexistente_lanzaResourceNotFound() {
        when(pacienteRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.obtenerPorId(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
