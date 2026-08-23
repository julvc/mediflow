package com.mediflow.api.service.impl;

import com.mediflow.api.domain.AccionAuditoria;
import com.mediflow.api.domain.Documento;
import com.mediflow.api.domain.EntidadAuditoria;
import com.mediflow.api.domain.EstadoDocumento;
import com.mediflow.api.domain.Turno;
import com.mediflow.api.dto.documento.CambioEstadoDocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.mapper.DocumentoMapper;
import com.mediflow.api.repository.DocumentoRepository;
import com.mediflow.api.repository.TurnoRepository;
import com.mediflow.api.service.AuditoriaService;
import com.mediflow.api.service.DocumentoService;
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
public class DocumentoServiceImpl implements DocumentoService {

    // Mismo enfoque que TurnoServiceImpl: máquina de estados como Map, no como
    // patrón State (3 valores fijos, sin ganancia de una jerarquía de clases).
    // PROCESADO y ERROR son terminales: el worker no reintenta un documento fallido,
    // sube uno nuevo (evita perder el motivo del error de un intento previo).
    private static final Map<EstadoDocumento, Set<EstadoDocumento>> TRANSICIONES_VALIDAS = new EnumMap<>(Map.of(
            EstadoDocumento.PENDIENTE, EnumSet.of(EstadoDocumento.PROCESADO, EstadoDocumento.ERROR),
            EstadoDocumento.PROCESADO, EnumSet.noneOf(EstadoDocumento.class),
            EstadoDocumento.ERROR, EnumSet.noneOf(EstadoDocumento.class)
    ));

    private final DocumentoRepository documentoRepository;
    private final TurnoRepository turnoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional
    public DocumentoResponse crear(DocumentoRequest request) {
        Turno turno = turnoRepository.findById(request.turnoId())
                .orElseThrow(() -> new ResourceNotFoundException("Turno " + request.turnoId() + " no encontrado"));
        Documento guardado = documentoRepository.save(DocumentoMapper.toEntity(request, turno));
        auditoriaService.registrar(AccionAuditoria.CREAR, EntidadAuditoria.DOCUMENTO, guardado.getId(),
                request.tipoDocumento());
        return DocumentoMapper.toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentoResponse obtenerPorId(Long id) {
        return DocumentoMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentoResponse> listarPorTurno(Long turnoId) {
        return documentoRepository.findByTurnoId(turnoId).stream()
                .map(DocumentoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DocumentoResponse cambiarEstado(Long id, CambioEstadoDocumentoRequest request) {
        Documento documento = buscarOFallar(id);
        EstadoDocumento actual = documento.getEstado();
        EstadoDocumento nuevo = request.estado();

        if (!TRANSICIONES_VALIDAS.get(actual).contains(nuevo)) {
            throw new ConflictoDeNegocioException("No se puede pasar de " + actual + " a " + nuevo);
        }

        documento.setEstado(nuevo);
        DocumentoResponse actualizado = DocumentoMapper.toResponse(documentoRepository.save(documento));
        auditoriaService.registrar(AccionAuditoria.CAMBIAR_ESTADO, EntidadAuditoria.DOCUMENTO, id, actual + " -> " + nuevo);
        return actualizado;
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (!documentoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Documento " + id + " no encontrado");
        }
        documentoRepository.deleteById(id);
        auditoriaService.registrar(AccionAuditoria.ELIMINAR, EntidadAuditoria.DOCUMENTO, id, null);
    }

    private Documento buscarOFallar(Long id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento " + id + " no encontrado"));
    }
}
