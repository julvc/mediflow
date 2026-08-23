package com.mediflow.api.service;

import com.mediflow.api.domain.Documento;
import com.mediflow.api.domain.EstadoDocumento;
import com.mediflow.api.domain.Paciente;
import com.mediflow.api.domain.Turno;
import com.mediflow.api.dto.documento.CambioEstadoDocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.DocumentoRepository;
import com.mediflow.api.repository.TurnoRepository;
import com.mediflow.api.service.impl.DocumentoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
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
class DocumentoServiceImplTest {

    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private DocumentoServiceImpl documentoService;

    private Turno turno;

    @BeforeEach
    void setUp() {
        Paciente paciente = Paciente.builder().id(1L).build();
        turno = Turno.builder().id(1L).paciente(paciente).build();
    }

    @Test
    void crear_conTurnoExistente_guardaEnEstadoPendiente() {
        DocumentoRequest request = new DocumentoRequest(1L, "examen.pdf", "s3://bucket/examen.pdf", "PDF");
        when(turnoRepository.findById(1L)).thenReturn(Optional.of(turno));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocacion -> {
            Documento documento = invocacion.getArgument(0);
            documento.setId(10L);
            return documento;
        });

        DocumentoResponse response = documentoService.crear(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
    }

    @Test
    void crear_conTurnoInexistente_lanzaResourceNotFound() {
        DocumentoRequest request = new DocumentoRequest(99L, "examen.pdf", "s3://bucket/examen.pdf", "PDF");
        when(turnoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentoService.crear(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(documentoRepository, never()).save(any());
    }

    @Test
    void obtenerPorId_conIdInexistente_lanzaResourceNotFound() {
        when(documentoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentoService.obtenerPorId(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cambiarEstado_dePendienteAProcesado_actualizaEstado() {
        Documento documento = Documento.builder().id(5L).turno(turno).estado(EstadoDocumento.PENDIENTE).build();
        when(documentoRepository.findById(5L)).thenReturn(Optional.of(documento));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        DocumentoResponse response = documentoService.cambiarEstado(5L, new CambioEstadoDocumentoRequest(EstadoDocumento.PROCESADO));

        assertThat(response.estado()).isEqualTo(EstadoDocumento.PROCESADO);
    }

    @Test
    void cambiarEstado_deProcesadoAPendiente_lanzaConflicto() {
        Documento documento = Documento.builder().id(5L).turno(turno).estado(EstadoDocumento.PROCESADO).build();
        when(documentoRepository.findById(5L)).thenReturn(Optional.of(documento));

        assertThatThrownBy(() -> documentoService.cambiarEstado(5L, new CambioEstadoDocumentoRequest(EstadoDocumento.PENDIENTE)))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(documentoRepository, never()).save(any());
    }

    @Test
    void eliminar_conIdInexistente_lanzaResourceNotFound() {
        when(documentoRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> documentoService.eliminar(404L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(documentoRepository, never()).deleteById(any());
    }
}
