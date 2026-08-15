package com.mediflow.api.service;

import com.mediflow.api.dto.turno.CambioEstadoTurnoRequest;
import com.mediflow.api.dto.turno.TurnoRequest;
import com.mediflow.api.dto.turno.TurnoResponse;

import java.util.List;

/**
 * Sin actualizar/eliminar genérico a propósito: en este dominio un turno no se
 * edita libremente, solo cambia de estado (agendar → confirmar/cancelar → completar).
 */
public interface TurnoService {

    TurnoResponse crear(TurnoRequest request);

    TurnoResponse obtenerPorId(Long id);

    List<TurnoResponse> listarTodos();

    List<TurnoResponse> listarPorPaciente(Long pacienteId);

    TurnoResponse cambiarEstado(Long id, CambioEstadoTurnoRequest request);
}
