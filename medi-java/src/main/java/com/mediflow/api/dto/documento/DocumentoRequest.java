package com.mediflow.api.dto.documento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Registra los metadatos de un documento ya subido a storage (S3/GCS).
 * El binario nunca pasa por esta API — solo su referencia (url_storage).
 */
public record DocumentoRequest(

        @NotNull(message = "el turno es obligatorio")
        Long turnoId,

        @NotBlank(message = "el nombre de archivo es obligatorio")
        String nombreArchivo,

        @NotBlank(message = "la url de storage es obligatoria")
        String urlStorage,

        @NotBlank(message = "el tipo de documento es obligatorio")
        String tipoDocumento
) {
}
