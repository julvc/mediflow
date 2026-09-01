package com.mediflow.api.service;

import com.mediflow.api.dto.profesional.ProfesionalRequest;
import com.mediflow.api.dto.profesional.ProfesionalResponse;

import java.util.List;

public interface ProfesionalService {

    ProfesionalResponse crear(ProfesionalRequest request);

    ProfesionalResponse obtenerPorId(Long id);

    List<ProfesionalResponse> listarTodos();

    ProfesionalResponse actualizar(Long id, ProfesionalRequest request);

    void eliminar(Long id);
}
