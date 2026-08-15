package com.mediflow.api.mapper;

import com.mediflow.api.domain.Documento;
import com.mediflow.api.domain.EstadoDocumento;
import com.mediflow.api.domain.Turno;
import com.mediflow.api.dto.documento.DocumentoRequest;
import com.mediflow.api.dto.documento.DocumentoResponse;

public final class DocumentoMapper {

    private DocumentoMapper() {
    }

    public static Documento toEntity(DocumentoRequest request, Turno turno) {
        return Documento.builder()
                .turno(turno)
                .nombreArchivo(request.nombreArchivo())
                .urlStorage(request.urlStorage())
                .tipoDocumento(request.tipoDocumento())
                .estado(EstadoDocumento.PENDIENTE) // estado inicial: aún no lo procesó el worker Python
                .build();
    }

    public static DocumentoResponse toResponse(Documento entity) {
        return new DocumentoResponse(
                entity.getId(),
                entity.getTurno().getId(),
                entity.getNombreArchivo(),
                entity.getUrlStorage(),
                entity.getTipoDocumento(),
                entity.getEstado()
        );
    }
}
