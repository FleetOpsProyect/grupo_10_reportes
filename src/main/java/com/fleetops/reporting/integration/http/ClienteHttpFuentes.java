package com.fleetops.reporting.integration.http;

import com.fleetops.reporting.application.fuentes.FuenteFallidaException;
import com.fleetops.reporting.config.ReportingProperties;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP de las fuentes (Adapter). Hace un GET, propaga X-Correlation-Id, valida que la respuesta traiga
 * los campos que necesitamos e ignora los demás. Cualquier problema se convierte en
 * {@link FuenteFallidaException}; el timeout llega como {@link HttpTimeoutException}.
 */
@Component
public class ClienteHttpFuentes {

    public static final String CABECERA_CORRELACION = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(ClienteHttpFuentes.class);

    private final HttpClient http;
    private final JsonMapper json;
    private final Map<String, String> urls;
    private final Duration timeout;

    public ClienteHttpFuentes(HttpClient httpClientFuentes, JsonMapper json, ReportingProperties props) {
        this.http = httpClientFuentes;
        this.json = json;
        this.urls = props.fuentes();
        this.timeout = props.timeout();
    }

    public JsonNode consultar(ConsultaHttp consulta, String correlationId) throws HttpTimeoutException {
        String fuente = consulta.fuente().codigo();
        URI uri = construirUri(consulta);
        HttpRequest.Builder peticion = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout)
                .header("Accept", "application/json");
        if (correlationId != null) {
            peticion.header(CABECERA_CORRELACION, correlationId);
        }
        long inicio = System.nanoTime();
        String resultado = "error";
        try {
            HttpResponse<String> respuesta = http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() < 200 || respuesta.statusCode() > 299) {
                throw new FuenteFallidaException("respondió HTTP " + respuesta.statusCode());
            }
            JsonNode cuerpo = leer(respuesta.body());
            validar(consulta, cuerpo);
            resultado = "ok";
            return cuerpo;
        } catch (HttpTimeoutException e) {
            resultado = "timeout";
            throw e;
        } catch (ConnectException e) {
            throw new FuenteFallidaException("conexión rechazada", e);
        } catch (IOException e) {
            throw new FuenteFallidaException("error de red: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FuenteFallidaException("llamada interrumpida", e);
        } finally {
            log.atInfo()
                    .addKeyValue("correlation_id", correlationId)
                    .addKeyValue("fuente", fuente)
                    .addKeyValue("consulta", consulta.id())
                    .addKeyValue("latencia_ms", (System.nanoTime() - inicio) / 1_000_000)
                    .addKeyValue("resultado", resultado)
                    .log("Consulta a la fuente {} ({}): {}", fuente, consulta.id(), resultado);
        }
    }

    private URI construirUri(ConsultaHttp consulta) {
        String base = urls.get(consulta.fuente().codigo());
        if (base == null || base.isBlank()) {
            throw new FuenteFallidaException("no tiene URL configurada (FUENTE_*_URL)");
        }
        String query = consulta.parametros().entrySet().stream()
                .map(e -> codificar(e.getKey()) + "=" + codificar(e.getValue()))
                .collect(Collectors.joining("&"));
        String sinBarra = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(sinBarra + consulta.ruta() + (query.isEmpty() ? "" : "?" + query));
    }

    private JsonNode leer(String cuerpo) {
        try {
            JsonNode nodo = json.readTree(cuerpo);
            if (nodo == null || !nodo.isObject()) {
                throw new FuenteFallidaException("la respuesta no es un objeto JSON");
            }
            return nodo;
        } catch (JacksonException e) {
            throw new FuenteFallidaException("la respuesta no es JSON válido", e);
        }
    }

    /** Al menos uno de los campos requeridos debe venir y con el tipo esperado (HU-02, criterio 4). */
    private static void validar(ConsultaHttp consulta, JsonNode cuerpo) {
        boolean valido = consulta.camposRequeridos().stream()
                .map(cuerpo::get)
                .anyMatch(campo -> campo != null && (consulta.numerico() ? campo.isNumber() : campo.isObject()));
        if (!valido) {
            throw new FuenteFallidaException("la respuesta no trae " + String.join(" ni ", consulta.camposRequeridos())
                    + (consulta.numerico() ? " numérico" : " como objeto"));
        }
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
