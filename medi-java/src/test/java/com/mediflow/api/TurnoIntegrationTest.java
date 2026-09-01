package com.mediflow.api;

import com.mediflow.api.dto.auth.LoginRequest;
import com.mediflow.api.dto.auth.LoginResponse;
import com.mediflow.api.dto.paciente.PacienteRequest;
import com.mediflow.api.dto.paciente.PacienteResponse;
import com.mediflow.api.dto.profesional.ProfesionalRequest;
import com.mediflow.api.dto.profesional.ProfesionalResponse;
import com.mediflow.api.dto.turno.TurnoRequest;
import com.mediflow.api.dto.turno.TurnoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración de extremo a extremo contra un Postgres REAL (no H2).
 * Es necesario Postgres real porque la migración V1 usa un índice único parcial
 * ("WHERE estado <> 'CANCELADO'"), sintaxis específica de Postgres que H2 no
 * reproduce igual — con H2 este test podría pasar sin validar la regla real.
 *
 * @ServiceConnection (Boot 3.1+) conecta automáticamente el DataSource de Spring
 * al contenedor de Testcontainers: sin esta anotación habría que registrar
 * manualmente spring.datasource.url/username/password con @DynamicPropertySource.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TurnoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders headersAdmin;

    // Tasks 1-9 (auth/roles) agregaron anyRequest().authenticated() a SecurityConfig
    // despues de que este test se escribiera — sin login, cada llamada devuelve 401.
    @BeforeEach
    void autenticarComoAdmin() {
        LoginResponse loginAdmin = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@mediflow.cl", "admin1234"), LoginResponse.class);
        headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(loginAdmin.accessToken());
    }

    @Test
    void creaPacienteProfesionalYTurno_flujoCompletoRetorna201YDatosCorrectos() {
        PacienteRequest pacienteRequest = new PacienteRequest(
                "11111111-1", "Camila", "Rojas",
                java.time.LocalDate.of(1992, 5, 20), "camila@test.cl", "911111111");
        ResponseEntity<PacienteResponse> pacienteResponse = restTemplate.exchange(
                "/api/v1/pacientes", HttpMethod.POST,
                new HttpEntity<>(pacienteRequest, headersAdmin), PacienteResponse.class);
        assertThat(pacienteResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long pacienteId = pacienteResponse.getBody().id();

        ProfesionalRequest profesionalRequest = new ProfesionalRequest(
                "22222222-2", "Jorge", "Perez", "Medicina General", "jorge@test.cl");
        ResponseEntity<ProfesionalResponse> profesionalResponse = restTemplate.exchange(
                "/api/v1/profesionales", HttpMethod.POST,
                new HttpEntity<>(profesionalRequest, headersAdmin), ProfesionalResponse.class);
        assertThat(profesionalResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long profesionalId = profesionalResponse.getBody().id();

        Instant fechaFutura = Instant.now().plus(2, ChronoUnit.DAYS);
        TurnoRequest turnoRequest = new TurnoRequest(pacienteId, profesionalId, fechaFutura, "Chequeo");
        ResponseEntity<TurnoResponse> turnoResponse = restTemplate.exchange(
                "/api/v1/turnos", HttpMethod.POST,
                new HttpEntity<>(turnoRequest, headersAdmin), TurnoResponse.class);

        assertThat(turnoResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(turnoResponse.getHeaders().getLocation()).isNotNull();
        TurnoResponse turnoCreado = turnoResponse.getBody();
        assertThat(turnoCreado.estado()).isEqualTo(com.mediflow.api.domain.EstadoTurno.PENDIENTE);
        assertThat(turnoCreado.nombrePaciente()).isEqualTo("Camila Rojas");
        assertThat(turnoCreado.nombreProfesional()).isEqualTo("Jorge Perez");

        ResponseEntity<TurnoResponse> turnoObtenido = restTemplate.exchange(
                "/api/v1/turnos/" + turnoCreado.id(), HttpMethod.GET,
                new HttpEntity<>(headersAdmin), TurnoResponse.class);
        assertThat(turnoObtenido.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(turnoObtenido.getBody().id()).isEqualTo(turnoCreado.id());
        assertThat(turnoObtenido.getBody().pacienteId()).isEqualTo(pacienteId);
    }
}
