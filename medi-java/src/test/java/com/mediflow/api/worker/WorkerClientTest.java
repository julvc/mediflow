package com.mediflow.api.worker;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Test unitario "puro" contra un servidor HTTP simulado (MockRestServiceServer),
// sin levantar worker_api.py real — mismo espiritu que TurnoServiceImplTest:
// aisla la logica del cliente de la infraestructura de red.
class WorkerClientTest {

    @Test
    void procesar_parseaLaRespuestaDelWorker() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://worker-fake/procesar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "nombreArchivo": "examen.pdf",
                          "tamanoBytes": 1234,
                          "paginas": 3,
                          "titulo": "Examen de sangre",
                          "autor": "Dra. Ana Soto",
                          "thumbnailPngBase64": "iVBORw0KGgo="
                        }
                        """, MediaType.APPLICATION_JSON));

        WorkerClient client = new WorkerClient(builder, "http://worker-fake");
        ResultadoProcesamiento resultado = client.procesar("contenido".getBytes(), "examen.pdf");

        assertThat(resultado.nombreArchivo()).isEqualTo("examen.pdf");
        assertThat(resultado.paginas()).isEqualTo(3);
        assertThat(resultado.titulo()).isEqualTo("Examen de sangre");
        assertThat(resultado.thumbnailPngBase64()).isEqualTo("iVBORw0KGgo=");
        servidor.verify();
    }
}
