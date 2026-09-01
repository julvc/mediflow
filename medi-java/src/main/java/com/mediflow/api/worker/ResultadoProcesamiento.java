package com.mediflow.api.worker;

/**
 * Espejo del JSON que devuelve POST /procesar en medi-python/worker_api.py.
 * Los nombres de campo ya vienen en camelCase desde el lado Python
 * (a proposito, para que Jackson los mapee sin configuracion extra).
 */
public record ResultadoProcesamiento(
        String nombreArchivo,
        long tamanoBytes,
        int paginas,
        String titulo,
        String autor,
        String thumbnailPngBase64
) {
}
