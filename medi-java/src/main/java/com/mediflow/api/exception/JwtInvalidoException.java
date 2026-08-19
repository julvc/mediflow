package com.mediflow.api.exception;

public class JwtInvalidoException extends RuntimeException {
    public JwtInvalidoException(String message) {
        super(message);
    }
}
