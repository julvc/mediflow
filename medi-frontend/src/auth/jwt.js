// El backend no expone GET /auth/me: el access token JWT ES el registro de
// usuario (usuarioId, rol, pacienteId, profesionalId van como claims), ver
// JwtService.generarAccessToken en el backend.
export function decodeJwt(token) {
  const payload = token.split('.')[1]
  const claims = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  return {
    usuarioId: claims.usuarioId,
    email: claims.sub,
    rol: claims.rol,
    pacienteId: claims.pacienteId ?? null,
    profesionalId: claims.profesionalId ?? null,
    exp: claims.exp,
  }
}
