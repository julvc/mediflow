package com.mediflow.api.service;

import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RefreshRequest;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;

public interface AuthService {
    UsuarioResponse registrar(RegistroRequest request);
    LoginResponse login(LoginRequest request);
    LoginResponse refrescar(RefreshRequest request);
    void logout(RefreshRequest request);
}
