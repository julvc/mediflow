package com.mediflow.api.service;

import com.mediflow.api.dto.paciente.PacienteRequest;
import com.mediflow.api.dto.paciente.PacienteResponse;

import java.util.List;

/**
 * El controller depende de esta interfaz, no de la implementación (DIP):
 * permite cambiar la lógica interna o mockearla en tests sin tocar la capa HTTP.
 */
public interface PacienteService {

    PacienteResponse crear(PacienteRequest request);

    PacienteResponse obtenerPorId(Long id);

    List<PacienteResponse> listarTodos();

    PacienteResponse actualizar(Long id, PacienteRequest request);

    void eliminar(Long id);
}
