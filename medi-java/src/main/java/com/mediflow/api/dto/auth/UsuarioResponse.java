package com.mediflow.api.dto.auth;

import com.mediflow.api.domain.Rol;

public record UsuarioResponse(Long id, String email, Rol rol) {
}
