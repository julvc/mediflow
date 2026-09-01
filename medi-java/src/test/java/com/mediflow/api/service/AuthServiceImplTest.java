package com.mediflow.api.service;

import com.mediflow.api.domain.Paciente;
import com.mediflow.api.domain.Rol;
import com.mediflow.api.domain.Usuario;
import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.exception.ConflictoDeNegocioException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.PacienteRepository;
import com.mediflow.api.repository.ProfesionalRepository;
import com.mediflow.api.repository.RefreshTokenRepository;
import com.mediflow.api.repository.UsuarioRepository;
import com.mediflow.api.security.JwtService;
import com.mediflow.api.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario "puro": sin Spring, sin base de datos — solo mockea los repositorios,
// igual patron que TurnoServiceImplTest.
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenMinutos", 15L);
        ReflectionTestUtils.setField(authService, "refreshTokenDias", 7L);
    }

    // SecurityContextHolder es un ThreadLocal estatico: si un test deja una
    // autenticacion cargada (o la limpia), eso puede filtrarse a otra clase de test
    // que corra en el mismo hilo (Surefire reusa hilos entre clases). Se limpia
    // siempre al terminar para que ningun otro test dependa del orden de ejecucion.
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_conCredencialesValidas_devuelveTokens() {
        Usuario usuario = Usuario.builder()
                .id(1L).email("ana@test.cl").passwordHash("hash").rol(Rol.PACIENTE).pacienteId(10L).activo(true).build();
        when(usuarioRepository.findByEmail("ana@test.cl")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave123", "hash")).thenReturn(true);
        when(jwtService.generarAccessToken(1L, "ana@test.cl", Rol.PACIENTE, 10L, null)).thenReturn("token-jwt");

        LoginResponse response = authService.login(new LoginRequest("ana@test.cl", "clave123"));

        assertThat(response.accessToken()).isEqualTo("token-jwt");
        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void login_conPasswordIncorrecto_lanzaBadCredentials() {
        Usuario usuario = Usuario.builder()
                .id(1L).email("ana@test.cl").passwordHash("hash").rol(Rol.PACIENTE).pacienteId(10L).activo(true).build();
        when(usuarioRepository.findByEmail("ana@test.cl")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@test.cl", "mala")))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_conEmailInexistente_lanzaBadCredentials() {
        when(usuarioRepository.findByEmail("nadie@test.cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@test.cl", "clave123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void registrar_conEmailDuplicado_lanzaConflicto() {
        when(usuarioRepository.existsByEmail("ana@test.cl")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("ana@test.cl", "clave1234", Rol.PACIENTE, 10L, null)))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_conPacienteInexistente_lanzaResourceNotFound() {
        when(usuarioRepository.existsByEmail("ana@test.cl")).thenReturn(false);
        when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("ana@test.cl", "clave1234", Rol.PACIENTE, 99L, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(usuarioRepository, never()).save(any());
    }

    // Fix Critical del review de rama: sin este chequeo, cualquier anonimo podia
    // registrarse con el pacienteId de OTRA persona (con tal de que esa ficha aun no
    // tuviera Usuario vinculado) y tomar control de ella. Se exige que el email del
    // request coincida con el email guardado en la ficha del Paciente.
    @Test
    void registrar_conEmailQueNoCoincideConElDeLaFichaDelPaciente_lanzaResourceNotFound() {
        Paciente paciente = Paciente.builder()
                .id(99L).email("dueno-real@test.cl").build();
        when(usuarioRepository.existsByEmail("atacante@test.cl")).thenReturn(false);
        when(pacienteRepository.findById(99L)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("atacante@test.cl", "clave1234", Rol.PACIENTE, 99L, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_comoAdminSinEstarAutenticadoComoAdmin_lanzaAccesoDenegado() {
        // Nadie autenticado en el SecurityContext (caso: llamada anonima a /registro).
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authService.registrar(
                new RegistroRequest("nuevo-admin@test.cl", "clave1234", Rol.ADMIN, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(usuarioRepository, never()).save(any());
    }
}
