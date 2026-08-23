package com.mediflow.api.service;

import com.mediflow.api.domain.AccionAuditoria;
import com.mediflow.api.domain.EntidadAuditoria;
import com.mediflow.api.dto.auditoria.RegistroAuditoriaResponse;

import java.util.List;

public interface AuditoriaService {

    /**
     * Registra una accion tomando el usuario autenticado del contexto de seguridad
     * actual. Si no hay usuario autenticado (ej: registro publico de PACIENTE),
     * queda registrada como "anonimo".
     */
    void registrar(AccionAuditoria accion, EntidadAuditoria entidad, Long entidadId, String detalle);

    /**
     * Igual que registrar(), pero con el usuario indicado explicitamente en vez de
     * leerlo del SecurityContext — necesario para LOGIN, momento en que aun no hay
     * autenticacion cargada en el contexto (recien se emitio el JWT, no se ha usado).
     */
    void registrarComoUsuario(Long usuarioId, String email, AccionAuditoria accion, EntidadAuditoria entidad,
                               Long entidadId, String detalle);

    List<RegistroAuditoriaResponse> listarRecientes();
}
