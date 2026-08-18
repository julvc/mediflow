package com.mediflow.api.service;

import com.mediflow.api.domain.Profesional;
import com.mediflow.api.dto.profesional.ProfesionalRequest;
import com.mediflow.api.dto.profesional.ProfesionalResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.service.impl.ProfesionalServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfesionalServiceImplTest {

    @Mock
    private ProfesionalRepository profesionalRepository;

    @InjectMocks
    private ProfesionalServiceImpl profesionalService;

    @Test
    void crear_conRutNuevo_guardaYDevuelveResponse() {
        ProfesionalRequest request = new ProfesionalRequest(
                "22222222-2", "Jorge", "Perez", "Medicina General", "jorge@test.cl");
        when(profesionalRepository.existsByRut("22222222-2")).thenReturn(false);
        when(profesionalRepository.save(any(Profesional.class))).thenAnswer(invocacion -> {
            Profesional profesional = invocacion.getArgument(0);
            profesional.setId(1L);
            return profesional;
        });

        ProfesionalResponse response = profesionalService.crear(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.rut()).isEqualTo("22222222-2");
    }

    @Test
    void crear_conRutYaRegistrado_lanzaConflicto() {
        ProfesionalRequest request = new ProfesionalRequest(
                "22222222-2", "Jorge", "Perez", "Medicina General", "jorge@test.cl");
        when(profesionalRepository.existsByRut("22222222-2")).thenReturn(true);

        assertThatThrownBy(() -> profesionalService.crear(request))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(profesionalRepository, never()).save(any());
    }

    @Test
    void obtenerPorId_conIdInexistente_lanzaResourceNotFound() {
        when(profesionalRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profesionalService.obtenerPorId(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
