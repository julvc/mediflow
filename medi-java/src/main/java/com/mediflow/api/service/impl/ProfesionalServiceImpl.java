package com.mediflow.api.service.impl;

import com.mediflow.api.domain.Profesional;
import com.mediflow.api.dto.profesional.ProfesionalRequest;
import com.mediflow.api.dto.profesional.ProfesionalResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.mapper.ProfesionalMapper;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.service.ProfesionalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfesionalServiceImpl implements ProfesionalService {

    private final ProfesionalRepository profesionalRepository;

    @Override
    public ProfesionalResponse crear(ProfesionalRequest request) {
        if (profesionalRepository.existsByRut(request.rut())) {
            throw new ConflictoDeNegocioException("Ya existe un profesional con rut " + request.rut());
        }
        Profesional guardado = profesionalRepository.save(ProfesionalMapper.toEntity(request));
        return ProfesionalMapper.toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfesionalResponse obtenerPorId(Long id) {
        return ProfesionalMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfesionalResponse> listarTodos() {
        return profesionalRepository.findAll().stream()
                .map(ProfesionalMapper::toResponse)
                .toList();
    }

    @Override
    public ProfesionalResponse actualizar(Long id, ProfesionalRequest request) {
        Profesional existente = buscarOFallar(id);
        ProfesionalMapper.actualizarEntidad(existente, request);
        return ProfesionalMapper.toResponse(profesionalRepository.save(existente));
    }

    @Override
    public void eliminar(Long id) {
        if (!profesionalRepository.existsById(id)) {
            throw new ResourceNotFoundException("Profesional " + id + " no encontrado");
        }
        profesionalRepository.deleteById(id);
    }

    private Profesional buscarOFallar(Long id) {
        return profesionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional " + id + " no encontrado"));
    }
}
