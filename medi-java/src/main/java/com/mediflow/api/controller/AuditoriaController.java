package com.mediflow.api.controller;

import com.mediflow.api.dto.auditoria.RegistroAuditoriaResponse;
import com.mediflow.api.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<RegistroAuditoriaResponse> listarRecientes() {
        return auditoriaService.listarRecientes();
    }
}
