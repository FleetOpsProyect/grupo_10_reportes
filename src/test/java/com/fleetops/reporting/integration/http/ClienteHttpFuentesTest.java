package com.fleetops.reporting.integration.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fleetops.reporting.Fixtures;
import com.fleetops.reporting.application.Fuente;
import com.fleetops.reporting.application.fuentes.FuenteFallidaException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Prueba el cliente contra un servidor HTTP local que simula a la fuente. */
class ClienteHttpFuentesTest {

    private HttpServer servidor;
    private final AtomicReference<String> cuerpo = new AtomicReference<>("{}");
    private final AtomicReference<Integer> estado = new AtomicReference<>(200);
    private final AtomicReference<Long> demoraMs = new AtomicReference<>(0L);
    private final AtomicReference<String> correlacionRecibida = new AtomicReference<>();
    private final AtomicReference<String> queryRecibida = new AtomicReference<>();
    private ClienteHttpFuentes cliente;

    private final ConsultaHttp vehiculos = new ConsultaHttp(Fuente.VEHICULOS, "resumen", "/vehiculos",
            Map.of("page", "0"), List.of("summary", "resumen"), false);

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            correlacionRecibida.set(intercambio.getRequestHeaders().getFirst("X-Correlation-Id"));
            queryRecibida.set(intercambio.getRequestURI().getRawQuery());
            try {
                Thread.sleep(demoraMs.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = cuerpo.get().getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado.get(), bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/";
        var props = Fixtures.props(null, Duration.ofMillis(400), 5, Map.of("vehiculos", url));
        cliente = new ClienteHttpFuentes(HttpClient.newHttpClient(), JsonMapper.builder().build(), props);
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    @Test
    void leeElResumenYPropagaElCorrelationId() throws Exception {
        cuerpo.set("{\"content\":[],\"resumen\":{\"total\":150},\"campo_nuevo\":true}");

        JsonNode respuesta = cliente.consultar(vehiculos, "corr-123");

        assertThat(respuesta.get("resumen").get("total").asInt()).isEqualTo(150);
        assertThat(correlacionRecibida).hasValue("corr-123");
        assertThat(queryRecibida).hasValue("page=0");
    }

    @Test
    void unaRespuesta5xxEsUnaFuenteFallida() {
        estado.set(503);

        assertThatThrownBy(() -> cliente.consultar(vehiculos, "c"))
                .isInstanceOf(FuenteFallidaException.class).hasMessageContaining("503");
    }

    @Test
    void unaRespuesta200SinElBloqueResumenEsUnaFuenteFallida() {
        cuerpo.set("{\"content\":[]}");

        assertThatThrownBy(() -> cliente.consultar(vehiculos, "c")).isInstanceOf(FuenteFallidaException.class);
    }

    @Test
    void unResumenConOtroTipoEsUnaFuenteFallida() {
        cuerpo.set("{\"summary\":\"150\"}");

        assertThatThrownBy(() -> cliente.consultar(vehiculos, "c")).isInstanceOf(FuenteFallidaException.class);
    }

    @Test
    void unCuerpoQueNoEsJsonEsUnaFuenteFallida() {
        cuerpo.set("<html>error</html>");

        assertThatThrownBy(() -> cliente.consultar(vehiculos, "c")).isInstanceOf(FuenteFallidaException.class);
    }

    @Test
    void unaRespuestaLentaTerminaEnTimeout() {
        demoraMs.set(1_500L);

        assertThatThrownBy(() -> cliente.consultar(vehiculos, "c")).isInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void conexionRechazadaEsUnaFuenteFallida() {
        servidor.stop(0);

        assertThatThrownBy(() -> cliente.consultar(vehiculos, "c")).isInstanceOf(FuenteFallidaException.class);
    }

    @Test
    void unaFuenteSinUrlConfiguradaEsUnaFuenteFallida() {
        var sinUrl = new ConsultaHttp(Fuente.COMBUSTIBLE, "periodo", "/fuel_records", Map.of(), List.of("summary"), false);

        assertThatThrownBy(() -> cliente.consultar(sinUrl, "c"))
                .isInstanceOf(FuenteFallidaException.class).hasMessageContaining("URL");
    }

    @Test
    void validaCamposNumericosComoTotalElements() throws Exception {
        var documentos = new ConsultaHttp(Fuente.VEHICULOS, "x", "/documents", Map.of(), List.of("total_elements"), true);
        cuerpo.set("{\"total_elements\":7}");

        assertThat(cliente.consultar(documentos, "c").get("total_elements").asInt()).isEqualTo(7);
    }
}
