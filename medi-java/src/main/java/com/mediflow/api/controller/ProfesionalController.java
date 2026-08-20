package com.mediflow.api.controller;

import com.mediflow.api.dto.profesional.ProfesionalRequest;
import com.mediflow.api.dto.profesional.ProfesionalResponse;
import com.mediflow.api.service.ProfesionalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/profesionales")
@RequiredArgsConstructor
public class ProfesionalController {

    private final ProfesionalService profesionalService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfesionalResponse> crear(@Valid @RequestBody ProfesionalRequest request) {
        ProfesionalResponse creado = profesionalService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROFESIONAL','ADMIN') or @recursoAuth.esPropioProfesional(authentication, #id)")
    public ProfesionalResponse obtenerPorId(@PathVariable Long id) {
        return profesionalService.obtenerPorId(id);
    }

    // Listado abierto a cualquier autenticado a proposito: un PACIENTE necesita poder
    // listar profesionales para agendar un turno.
    @GetMapping
    public List<ProfesionalResponse> listarTodos() {
        return profesionalService.listarTodos();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @recursoAuth.esPropioProfesional(authentication, #id)")
    public ProfesionalResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProfesionalRequest request) {
        return profesionalService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        profesionalService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
