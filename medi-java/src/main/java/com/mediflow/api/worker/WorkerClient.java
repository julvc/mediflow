package com.mediflow.api.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia medi-python/worker_api.py — el mismo procesador de
 * documentos que medi-python/backend usa directo como libreria. Java no
 * puede importar Python, asi que este es el punto de integracion: ambos
 * backends terminan corriendo exactamente el mismo codigo de procesamiento.
 *
 * Sin usar todavia desde ningun controller (el flujo actual de Documento es
 * solo referencia por urlStorage, a proposito, por ser una POC) — este
 * cliente deja el contrato probado y listo para el dia que DocumentoController
 * necesite disparar el procesamiento real de un archivo subido.
 */
@Component
public class WorkerClient {

    private final RestClient restClient;

    public WorkerClient(RestClient.Builder builder, @Value("${mediflow.worker.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ResultadoProcesamiento procesar(byte[] contenido, String nombreArchivo) {
        ByteArrayResource archivo = new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("archivo", archivo);

        return restClient.post()
                .uri("/procesar")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(ResultadoProcesamiento.class);
    }
}
