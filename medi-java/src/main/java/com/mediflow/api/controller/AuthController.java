package com.mediflow.api.controller;

import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RefreshRequest;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;
import com.mediflow.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refrescar(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
}
