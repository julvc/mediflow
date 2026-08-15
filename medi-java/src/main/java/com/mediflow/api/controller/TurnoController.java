package com.mediflow.api.controller;

import com.mediflow.api.dto.turno.CambioEstadoTurnoRequest;
import com.mediflow.api.dto.turno.TurnoRequest;
import com.mediflow.api.dto.turno.TurnoResponse;
import com.mediflow.api.service.TurnoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/turnos")
@RequiredArgsConstructor
public class TurnoController {

    private final TurnoService turnoService;

    @PostMapping
    public ResponseEntity<TurnoResponse> crear(@Valid @RequestBody TurnoRequest request) {
        TurnoResponse creado = turnoService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @GetMapping("/{id}")
    public TurnoResponse obtenerPorId(@PathVariable Long id) {
        return turnoService.obtenerPorId(id);
    }

    // Un solo endpoint de listado: si viene pacienteId filtra, si no trae todos.
    // Evita duplicar la ruta de listado en /turnos y /turnos/por-paciente.
    @GetMapping
    public List<TurnoResponse> listar(@RequestParam(required = false) Long pacienteId) {
        return pacienteId != null ? turnoService.listarPorPaciente(pacienteId) : turnoService.listarTodos();
    }

    @PatchMapping("/{id}/estado")
    public TurnoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoTurnoRequest request) {
        return turnoService.cambiarEstado(id, request);
    }
}
