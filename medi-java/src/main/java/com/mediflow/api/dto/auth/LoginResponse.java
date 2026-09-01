package com.mediflow.api.dto.auth;

public record LoginResponse(String accessToken, String refreshToken, long expiraEnSegundos) {
}
