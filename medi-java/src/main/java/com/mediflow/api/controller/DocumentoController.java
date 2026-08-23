package com.mediflow.api.controller;

import com.mediflow.api.dto.documento.CambioEstadoDocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoResponse;
import com.mediflow.api.service.DocumentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;

    @PostMapping
    public ResponseEntity<DocumentoResponse> crear(@Valid @RequestBody DocumentoRequest request) {
        DocumentoResponse creado = documentoService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.id())
                .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    // @PostAuthorize (no @PreAuthorize): el dueno real es el Paciente del Turno del
    // Documento, dato que solo se conoce despues de cargarlo. Mismo patron que
    // TurnoController.obtenerPorId, reusando esPropioPaciente contra returnObject.
    @GetMapping("/{id}")
    @PostAuthorize("hasAnyRole('PROFESIONAL','ADMIN') or @recursoAuth.esPropioPaciente(authentication, returnObject.pacienteId())")
    public DocumentoResponse obtenerPorId(@PathVariable Long id) {
        return documentoService.obtenerPorId(id);
    }

    // turnoId es obligatorio: listar por turno es el único caso de uso pedido,
    // un listado global no tiene consumidor real hoy (YAGNI).
    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESIONAL','ADMIN')")
    public List<DocumentoResponse> listarPorTurno(@RequestParam Long turnoId) {
        return documentoService.listarPorTurno(turnoId);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('PROFESIONAL','ADMIN')")
    public DocumentoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoDocumentoRequest request) {
        return documentoService.cambiarEstado(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
