package com.mediflow.api.service.impl;

import com.mediflow.api.domain.AccionAuditoria;
import com.mediflow.api.domain.EntidadAuditoria;
import com.mediflow.api.domain.Paciente;
import com.mediflow.api.domain.RefreshToken;
import com.mediflow.api.domain.Rol;
import com.mediflow.api.domain.Usuario;
import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RefreshRequest;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.JwtInvalidoException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.repository.RefreshTokenRepository;
import com.mediflow.api.repository.UsuarioRepository;
import com.mediflow.api.security.JwtService;
import com.mediflow.api.service.AuditoriaService;
import com.mediflow.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditoriaService auditoriaService;

    @Value("${mediflow.jwt.access-token-minutos}")
    private long accessTokenMinutos;

    @Value("${mediflow.jwt.refresh-token-dias}")
    private long refreshTokenDias;

    @Override
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        // El registro publico (sin token) solo puede crear cuentas PACIENTE. Crear
        // PROFESIONAL o ADMIN exige ya estar autenticado como ADMIN — si no, cualquier
        // anonimo podria auto-otorgarse rol ADMIN llamando este mismo endpoint publico.
        if (request.rol() != Rol.PACIENTE && !solicitanteEsAdmin()) {
            throw new AccessDeniedException(
                    "Solo un administrador puede registrar cuentas de PROFESIONAL o ADMIN");
        }

        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflictoDeNegocioException("Ya existe un usuario con el email " + request.email());
        }
        validarVinculoDeDominio(request.rol(), request.email(), request.pacienteId(), request.profesionalId());

        Usuario usuario = Usuario.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .pacienteId(request.pacienteId())
                .profesionalId(request.profesionalId())
                .activo(true)
                .build();
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrar(AccionAuditoria.REGISTRO, EntidadAuditoria.USUARIO, usuario.getId(),
                usuario.getRol() + " " + usuario.getEmail());
        return new UsuarioResponse(usuario.getId(), usuario.getEmail(), usuario.getRol());
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Email o contraseña incorrectos");
        }

        // Login corre sin autenticacion previa en el SecurityContext (el JWT recien se
        // va a emitir), por eso el actor se pasa explicito en vez de leerlo del contexto.
        auditoriaService.registrarComoUsuario(usuario.getId(), usuario.getEmail(),
                AccionAuditoria.LOGIN, EntidadAuditoria.USUARIO, usuario.getId(), null);
        return generarTokens(usuario);
    }

    @Override
    @Transactional
    public LoginResponse refrescar(RefreshRequest request) {
        String hash = hashSha256(request.refreshToken());
        RefreshToken guardado = refreshTokenRepository.findByTokenHashAndRevocadoFalse(hash)
                .orElseThrow(() -> new JwtInvalidoException("Refresh token invalido o revocado"));

        if (guardado.getExpiraEn().isBefore(Instant.now())) {
            throw new JwtInvalidoException("Refresh token expirado");
        }

        Usuario usuario = usuarioRepository.findById(guardado.getUsuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario " + guardado.getUsuarioId() + " no encontrado"));

        // Rotacion: el refresh token usado se revoca y se emite uno nuevo. Si alguien
        // reutiliza un refresh token ya canjeado, ese segundo intento falla (ya revocado).
        guardado.setRevocado(true);
        refreshTokenRepository.save(guardado);

        return generarTokens(usuario);
    }

    @Override
    @Transactional
    public void logout(RefreshRequest request) {
        String hash = hashSha256(request.refreshToken());
        refreshTokenRepository.findByTokenHashAndRevocadoFalse(hash).ifPresent(token -> {
            token.setRevocado(true);
            refreshTokenRepository.save(token);
        });
    }

    private LoginResponse generarTokens(Usuario usuario) {
        String accessToken = jwtService.generarAccessToken(
                usuario.getId(), usuario.getEmail(), usuario.getRol(), usuario.getPacienteId(), usuario.getProfesionalId());

        String refreshTokenPlano = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .usuarioId(usuario.getId())
                .tokenHash(hashSha256(refreshTokenPlano))
                .expiraEn(Instant.now().plusSeconds(refreshTokenDias * 24 * 60 * 60))
                .revocado(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, refreshTokenPlano, accessTokenMinutos * 60);
    }

    private void validarVinculoDeDominio(Rol rol, String email, Long pacienteId, Long profesionalId) {
        switch (rol) {
            // El registro publico de PACIENTE no exige autenticacion previa (a diferencia de
            // PROFESIONAL, que solo lo puede vincular un ADMIN ya autenticado - ver el chequeo
            // solicitanteEsAdmin() en registrar()). Sin este chequeo de email, cualquier anonimo
            // podria vincularse a la ficha de OTRO paciente con solo conocer su pacienteId,
            // tomando control de esa ficha. Se usa el mismo mensaje que "no encontrado" para no
            // revelar via un mensaje distinto que el paciente existe pero el email no coincide
            // (eso seria un oraculo para enumerar pacientes por id).
            case PACIENTE -> {
                Paciente paciente = pacienteId == null ? null : pacienteRepository.findById(pacienteId).orElse(null);
                if (paciente == null || !paciente.getEmail().equalsIgnoreCase(email)) {
                    throw new ResourceNotFoundException("Paciente " + pacienteId + " no encontrado");
                }
            }
            case PROFESIONAL -> {
                if (profesionalId == null || !profesionalRepository.existsById(profesionalId)) {
                    throw new ResourceNotFoundException("Profesional " + profesionalId + " no encontrado");
                }
            }
            case ADMIN -> {
                // Sin vinculo de dominio.
            }
        }
    }

    private boolean solicitanteEsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String hashSha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
