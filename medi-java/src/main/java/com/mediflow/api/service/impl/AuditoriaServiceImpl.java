package com.mediflow.api.service.impl;

import com.mediflow.api.domain.AccionAuditoria;
import com.mediflow.api.domain.EntidadAuditoria;
import com.mediflow.api.domain.RegistroAuditoria;
import com.mediflow.api.dto.auditoria.RegistroAuditoriaResponse;
import com.mediflow.api.repository.RegistroAuditoriaRepository;
import com.mediflow.api.security.UsuarioPrincipal;
import com.mediflow.api.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditoriaServiceImpl implements AuditoriaService {

    // Ultimos 200 movimientos: alcanza para una bitacora de control interno de este
    // volumen; si algun dia hace falta ir mas atras, se agrega paginacion real.
    private static final int LIMITE_RECIENTES = 200;

    private final RegistroAuditoriaRepository registroAuditoriaRepository;

    @Override
    @Transactional
    public void registrar(AccionAuditoria accion, EntidadAuditoria entidad, Long entidadId, String detalle) {
        UsuarioPrincipal principal = usuarioActual();
        Long usuarioId = principal != null ? principal.getUsuarioId() : null;
        String email = principal != null ? principal.getEmail() : "anonimo";
        registrarComoUsuario(usuarioId, email, accion, entidad, entidadId, detalle);
    }

    @Override
    @Transactional
    public void registrarComoUsuario(Long usuarioId, String email, AccionAuditoria accion, EntidadAuditoria entidad,
                                      Long entidadId, String detalle) {
        RegistroAuditoria registro = RegistroAuditoria.builder()
                .usuarioId(usuarioId)
                .usuarioEmail(email)
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId)
                .detalle(detalle)
                .creadoEn(Instant.now())
                .build();
        registroAuditoriaRepository.save(registro);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistroAuditoriaResponse> listarRecientes() {
        return registroAuditoriaRepository.findAllByOrderByCreadoEnDesc(PageRequest.of(0, LIMITE_RECIENTES)).stream()
                .map(r -> new RegistroAuditoriaResponse(
                        r.getId(), r.getUsuarioEmail(), r.getAccion(), r.getEntidad(), r.getEntidadId(),
                        r.getDetalle(), r.getCreadoEn()))
                .toList();
    }

    private UsuarioPrincipal usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
