package com.mediflow.api.service.impl;

import com.mediflow.api.domain.Documento;
import com.mediflow.api.domain.Turno;
import com.mediflow.api.dto.documento.DocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoResponse;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.mapper.DocumentoMapper;
import com.mediflow.api.repository.DocumentoRepository;
import com.mediflow.api.repository.TurnoRepository;
import com.mediflow.api.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentoServiceImpl implements DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final TurnoRepository turnoRepository;

    @Override
    @Transactional
    public DocumentoResponse crear(DocumentoRequest request) {
        Turno turno = turnoRepository.findById(request.turnoId())
                .orElseThrow(() -> new ResourceNotFoundException("Turno " + request.turnoId() + " no encontrado"));
        Documento guardado = documentoRepository.save(DocumentoMapper.toEntity(request, turno));
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
    public void eliminar(Long id) {
        if (!documentoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Documento " + id + " no encontrado");
        }
        documentoRepository.deleteById(id);
    }

    private Documento buscarOFallar(Long id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento " + id + " no encontrado"));
    }
}
