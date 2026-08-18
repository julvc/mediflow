package com.mediflow.api.service;

import com.mediflow.api.dto.documento.CambioEstadoDocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoResponse;

import java.util.List;

public interface DocumentoService {

    DocumentoResponse crear(DocumentoRequest request);

    DocumentoResponse obtenerPorId(Long id);

    List<DocumentoResponse> listarPorTurno(Long turnoId);

    DocumentoResponse cambiarEstado(Long id, CambioEstadoDocumentoRequest request);

    void eliminar(Long id);
}
