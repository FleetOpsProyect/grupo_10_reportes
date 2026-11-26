package com.fleetops.reporting.api;

import com.fleetops.reporting.application.ReporteDisponibilidad;
import com.fleetops.reporting.application.ServicioReporte;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /reportes/disponibilidad (HU-01). Solo valida la entrada y delega en {@link ServicioReporte}. */
@RestController
@RequestMapping("/reportes")
@Tag(name = "Reportes operativos", description = "Tablero de KPIs de la flota (Grupo 10)")
public class ReporteController {

    private final ServicioReporte servicio;

    public ReporteController(ServicioReporte servicio) {
        this.servicio = servicio;
    }

    @GetMapping(value = "/disponibilidad", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tablero de KPIs operativos de la flota",
            description = "Sin parámetros devuelve todo el histórico. El estado actual sale por HTTP de cada "
                    + "microservicio; el período sale de la base de lectura (eventos) o, si el rango empieza antes "
                    + "de REPORTES_EVENTOS_DESDE, de los resúmenes HTTP. Si alguna fuente falla responde 200 con sus "
                    + "KPIs en null y la fuente en fuentes_no_disponibles.")
    @ApiResponse(responseCode = "200", description = "Reporte completo o parcial", content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            examples = {
                    @ExampleObject(name = "completo", value = EjemplosOpenApi.COMPLETO),
                    @ExampleObject(name = "parcial", value = EjemplosOpenApi.PARCIAL)}))
    @ApiResponse(responseCode = "400", description = "Rango inválido", content = @Content(
            schema = @Schema(implementation = ErrorRespuesta.class),
            examples = @ExampleObject(value = EjemplosOpenApi.RANGO_INVALIDO)))
    @ApiResponse(responseCode = "503", description = "Ninguna fuente respondió", content = @Content(
            schema = @Schema(implementation = ErrorRespuesta.class),
            examples = @ExampleObject(value = EjemplosOpenApi.SIN_FUENTES)))
    public ReporteDisponibilidad disponibilidad(
            @Parameter(description = "HOY, SEMANA_ACTUAL, MES_ACTUAL o PERSONALIZADO")
            @RequestParam(name = "rango", required = false) String rango,
            @Parameter(description = "AAAA-MM-DD; obligatorio con PERSONALIZADO")
            @RequestParam(name = "fecha_desde", required = false) String fechaDesde,
            @Parameter(description = "AAAA-MM-DD; obligatorio con PERSONALIZADO")
            @RequestParam(name = "fecha_hasta", required = false) String fechaHasta,
            HttpServletRequest peticion) {
        return servicio.generar(rango, fechaDesde, fechaHasta, FiltroCorrelacion.de(peticion));
    }
}
