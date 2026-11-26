package com.fleetops.reporting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Datos generales del contrato OpenAPI publicado en /docs (HU-01, criterio 14). */
@Configuration
public class ConfiguracionOpenApi {

    @Bean
    OpenAPI contratoReportes() {
        return new OpenAPI().info(new Info()
                .title("FleetOps · MS Reportes Operativos (Grupo 10)")
                .version("1.0")
                .description("Tablero de KPIs operativos de la flota. Modelo híbrido: el estado actual sale por HTTP "
                        + "de cada microservicio y el período sale de la base de lectura propia, alimentada por "
                        + "eventos SNS→SQS. El 401 lo responde el API Gateway."));
    }
}
