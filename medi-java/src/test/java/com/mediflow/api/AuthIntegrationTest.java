package com.mediflow.api;

import com.mediflow.api.domain.Rol;
import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.auth.RefreshRequest;
import com.mediflow.api.dto.auth.RegistroRequest;
import com.mediflow.api.dto.auth.UsuarioResponse;
import com.mediflow.api.dto.paciente.PacienteRequest;
import com.mediflow.api.dto.paciente.PacienteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    // El cliente JDK por defecto de TestRestTemplate (no hay Apache HttpClient5/OkHttp
    // en el classpath) usa el legacy sun.net.www.protocol.http.HttpURLConnection, cuyo
    // modo de streaming del cuerpo del POST choca con su propia logica interna de
    // reintento de autenticacion en cuanto la respuesta es 401: revienta con
    // "HttpRetryException: cannot retry due to server authentication, in streaming mode"
    // en vez de simplemente devolver el 401 (reproducible incluso desactivando el
    // streaming de SimpleClientHttpRequestFactory — es un limite del cliente legacy).
    // JdkClientHttpRequestFactory (Spring 6.1+) usa java.net.http.HttpClient, el
    // cliente HTTP moderno de la stdlib desde Java 11, que no tiene este problema.
    @BeforeEach
    void usarClienteHttpModernoDeLaStdlib() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void endpointProtegido_sinToken_devuelve401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/pacientes/1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginComoAdmin_crearPacienteYUsuario_loginComoPaciente_yVerSuPropioRegistro() {
        // El admin semilla (V2__auth_schema.sql) es el unico usuario que existe al
        // arrancar — se usa para crear al Paciente y su cuenta.
        LoginResponse loginAdmin = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@mediflow.cl", "admin1234"), LoginResponse.class);
        assertThat(loginAdmin.accessToken()).isNotBlank();

        HttpHeaders headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(loginAdmin.accessToken());

        PacienteRequest pacienteRequest = new PacienteRequest(
                "33333333-3", "Fernanda", "Lopez",
                java.time.LocalDate.of(1990, 3, 15), "fernanda@test.cl", "922222222");
        ResponseEntity<PacienteResponse> pacienteResponse = restTemplate.exchange(
                "/api/v1/pacientes", HttpMethod.POST,
                new HttpEntity<>(pacienteRequest, headersAdmin), PacienteResponse.class);
        assertThat(pacienteResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long pacienteId = pacienteResponse.getBody().id();

        RegistroRequest registroRequest = new RegistroRequest(
                "fernanda@test.cl", "clave12345", Rol.PACIENTE, pacienteId, null);
        ResponseEntity<UsuarioResponse> registroResponse = restTemplate.postForEntity(
                "/api/v1/auth/registro", registroRequest, UsuarioResponse.class);
        assertThat(registroResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginResponse loginPaciente = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("fernanda@test.cl", "clave12345"), LoginResponse.class);
        assertThat(loginPaciente.accessToken()).isNotBlank();
        assertThat(loginPaciente.refreshToken()).isNotBlank();

        HttpHeaders headersPaciente = new HttpHeaders();
        headersPaciente.setBearerAuth(loginPaciente.accessToken());
        ResponseEntity<PacienteResponse> propioPaciente = restTemplate.exchange(
                "/api/v1/pacientes/" + pacienteId, HttpMethod.GET,
                new HttpEntity<>(headersPaciente), PacienteResponse.class);
        assertThat(propioPaciente.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(propioPaciente.getBody().id()).isEqualTo(pacienteId);
    }

    // Fix Important del review de rama: sin este test, si RecursoAuthService.esPropioPaciente
    // devolviera true siempre (bug), la suite completa seguiria en verde sin detectarlo.
    @Test
    void pacienteAutenticado_intentaVerFichaDeOtroPaciente_devuelve403() {
        LoginResponse loginAdmin = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@mediflow.cl", "admin1234"), LoginResponse.class);
        HttpHeaders headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(loginAdmin.accessToken());

        PacienteRequest pacienteRequestFernanda = new PacienteRequest(
                "55555555-5", "Fernanda", "Lopez",
                java.time.LocalDate.of(1990, 3, 15), "fernanda2@test.cl", "922222222");
        ResponseEntity<PacienteResponse> pacienteResponseFernanda = restTemplate.exchange(
                "/api/v1/pacientes", HttpMethod.POST,
                new HttpEntity<>(pacienteRequestFernanda, headersAdmin), PacienteResponse.class);
        assertThat(pacienteResponseFernanda.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long pacienteIdFernanda = pacienteResponseFernanda.getBody().id();

        RegistroRequest registroRequestFernanda = new RegistroRequest(
                "fernanda2@test.cl", "clave12345", Rol.PACIENTE, pacienteIdFernanda, null);
        ResponseEntity<UsuarioResponse> registroResponseFernanda = restTemplate.postForEntity(
                "/api/v1/auth/registro", registroRequestFernanda, UsuarioResponse.class);
        assertThat(registroResponseFernanda.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginResponse loginFernanda = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("fernanda2@test.cl", "clave12345"), LoginResponse.class);
        HttpHeaders headersFernanda = new HttpHeaders();
        headersFernanda.setBearerAuth(loginFernanda.accessToken());

        // Segundo paciente distinto, sin cuenta propia vinculada.
        PacienteRequest pacienteRequestOtro = new PacienteRequest(
                "66666666-6", "Marcelo", "Diaz",
                java.time.LocalDate.of(1985, 7, 1), "marcelo@test.cl", "933333333");
        ResponseEntity<PacienteResponse> pacienteResponseOtro = restTemplate.exchange(
                "/api/v1/pacientes", HttpMethod.POST,
                new HttpEntity<>(pacienteRequestOtro, headersAdmin), PacienteResponse.class);
        assertThat(pacienteResponseOtro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long pacienteIdOtro = pacienteResponseOtro.getBody().id();

        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/api/v1/pacientes/" + pacienteIdOtro, HttpMethod.GET,
                new HttpEntity<>(headersFernanda), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registrarAdmin_sinAutenticacion_devuelve403() {
        RegistroRequest registroAdmin = new RegistroRequest("otro-admin@test.cl", "clave12345", Rol.ADMIN, null, null);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/registro", registroAdmin, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // Agregado tras el review de Task 6: la rotacion de refresh token (AuthServiceImpl.refrescar)
    // no tenia ningun test automatizado que la protegiera de una regresion futura — este es el
    // unico lugar del plan que prueba que reusar un refresh token ya canjeado falla.
    @Test
    void refrescar_conRefreshTokenYaCanjeado_devuelve401() {
        LoginResponse loginAdmin = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@mediflow.cl", "admin1234"), LoginResponse.class);
        String refreshTokenOriginal = loginAdmin.refreshToken();

        ResponseEntity<LoginResponse> primerRefresh = restTemplate.postForEntity(
                "/api/v1/auth/refresh", new RefreshRequest(refreshTokenOriginal), LoginResponse.class);
        assertThat(primerRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(primerRefresh.getBody().refreshToken()).isNotEqualTo(refreshTokenOriginal);

        // El token original ya fue canjeado (revocado) por el refresh anterior — reusarlo debe fallar.
        ResponseEntity<String> segundoRefresh = restTemplate.postForEntity(
                "/api/v1/auth/refresh", new RefreshRequest(refreshTokenOriginal), String.class);
        assertThat(segundoRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
