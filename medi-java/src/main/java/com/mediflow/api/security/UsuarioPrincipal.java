package com.mediflow.api.security;

import com.mediflow.api.domain.Rol;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UsuarioPrincipal implements UserDetails {

    private final Long usuarioId;
    private final String email;
    private final Rol rol;
    private final Long pacienteId;
    private final Long profesionalId;

    public UsuarioPrincipal(Long usuarioId, String email, Rol rol, Long pacienteId, Long profesionalId) {
        this.usuarioId = usuarioId;
        this.email = email;
        this.rol = rol;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        // No aplica: la autenticacion ya ocurrio al validar el JWT, no se vuelve a
        // chequear password en cada request.
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
