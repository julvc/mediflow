package com.mediflow.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("recursoAuth")
public class RecursoAuthService {

    public boolean esPropioPaciente(Authentication authentication, Long pacienteId) {
        if (!(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return false;
        }
        return pacienteId.equals(principal.getPacienteId());
    }

    public boolean esPropioProfesional(Authentication authentication, Long profesionalId) {
        if (!(authentication.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return false;
        }
        return profesionalId.equals(principal.getProfesionalId());
    }
}
