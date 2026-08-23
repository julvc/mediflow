package com.mediflow.api.service.impl;

import com.mediflow.api.domain.AccionAuditoria;
import com.mediflow.api.domain.EntidadAuditoria;
import com.mediflow.api.domain.Paciente;
import com.mediflow.api.dto.paciente.PacienteRequest;
import com.mediflow.api.dto.paciente.PacienteResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.mapper.PacienteMapper;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.service.AuditoriaService;
import com.mediflow.api.service.PacienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional // a nivel de clase: la mayoría de los métodos son de escritura
public class PacienteServiceImpl implements PacienteService {

    private final PacienteRepository pacienteRepository;
    private final AuditoriaService auditoriaService;

    @Override
    public PacienteResponse crear(PacienteRequest request) {
        if (pacienteRepository.existsByRut(request.rut())) {
            throw new ConflictoDeNegocioException("Ya existe un paciente con rut " + request.rut());
        }
        Paciente guardado = pacienteRepository.save(PacienteMapper.toEntity(request));
        auditoriaService.registrar(AccionAuditoria.CREAR, EntidadAuditoria.PACIENTE, guardado.getId(), null);
        return PacienteMapper.toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public PacienteResponse obtenerPorId(Long id) {
        return PacienteMapper.toResponse(buscarOFallar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacienteResponse> listarTodos() {
        return pacienteRepository.findAll().stream()
                .map(PacienteMapper::toResponse)
                .toList();
    }

    @Override
    public PacienteResponse actualizar(Long id, PacienteRequest request) {
        Paciente existente = buscarOFallar(id);
        PacienteMapper.actualizarEntidad(existente, request);
        // save() es redundante bajo @Transactional (dirty-checking ya lo persiste),
        // se deja explícito por claridad: hace visible la intención de guardar.
        PacienteResponse actualizado = PacienteMapper.toResponse(pacienteRepository.save(existente));
        auditoriaService.registrar(AccionAuditoria.ACTUALIZAR, EntidadAuditoria.PACIENTE, id, null);
        return actualizado;
    }

    @Override
    public void eliminar(Long id) {
        if (!pacienteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Paciente " + id + " no encontrado");
        }
        pacienteRepository.deleteById(id);
        auditoriaService.registrar(AccionAuditoria.ELIMINAR, EntidadAuditoria.PACIENTE, id, null);
    }

    private Paciente buscarOFallar(Long id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente " + id + " no encontrado"));
    }
}
