package com.mediflow.api.controller;

import com.mediflow.api.dto.documento.CambioEstadoDocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoResponse;
import com.mediflow.api.service.DocumentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{id}")
    public DocumentoResponse obtenerPorId(@PathVariable Long id) {
        return documentoService.obtenerPorId(id);
    }

    // turnoId es obligatorio: listar por turno es el único caso de uso pedido,
    // un listado global no tiene consumidor real hoy (YAGNI).
    @GetMapping
    public List<DocumentoResponse> listarPorTurno(@RequestParam Long turnoId) {
        return documentoService.listarPorTurno(turnoId);
    }

    @PatchMapping("/{id}/estado")
    public DocumentoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoDocumentoRequest request) {
        return documentoService.cambiarEstado(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
