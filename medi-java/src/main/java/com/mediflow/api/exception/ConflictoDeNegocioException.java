package com.mediflow.api.exception;

/**
 * Regla de negocio violada (ej: turno duplicado). Se traduce a HTTP 409.
 */
public class ConflictoDeNegocioException extends RuntimeException {
    public ConflictoDeNegocioException(String message) {
        super(message);
    }
}
