package com.fleetops.reporting.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans técnicos del servicio. */
@Configuration
@EnableConfigurationProperties(ReportingProperties.class)
public class ConfiguracionReportes {

    /** Hora de Colombia (GMT-5): con ella se calculan los rangos y fecha_generacion. */
    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @Bean
    Clock reloj() {
        return Clock.system(ZONA_COLOMBIA);
    }

    /**
     * Pool propio para las consultas a las fuentes (Bulkhead): si todas las fuentes están lentas, se ocupan estos
     * hilos y no los de Tomcat, así /health y /readyz siguen respondiendo.
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService ejecutorFuentes() {
        return Executors.newFixedThreadPool(24, Thread.ofPlatform().name("fuente-", 0).daemon(true).factory());
    }

    /** Cliente HTTP de las fuentes, con su propio pool de conexiones e hilos (no comparte con el resto). */
    @Bean
    HttpClient httpClientFuentes(ReportingProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                .executor(Executors.newFixedThreadPool(8, Thread.ofPlatform().name("http-fuentes-", 0).daemon(true).factory()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
