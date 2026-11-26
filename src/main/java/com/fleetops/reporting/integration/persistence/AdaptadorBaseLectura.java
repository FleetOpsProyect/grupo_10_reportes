package com.fleetops.reporting.integration.persistence;

import com.fleetops.reporting.application.Fuente;
import com.fleetops.reporting.application.PeriodoConsulta;
import com.fleetops.reporting.application.fuentes.PuertoBaseLectura;
import com.fleetops.reporting.application.fuentes.SolicitudConsulta;
import com.fleetops.reporting.config.ReportingProperties;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lectura de la base de lectura (Neon) como una fuente más del Scatter-Gather, con su propio Circuit Breaker
 * y el mismo timeout. Si la base no responde, "base_lectura" aparece en fuentes_no_disponibles y los KPIs por
 * HTTP se siguen entregando (HU-02, criterio 12).
 */
@Component
public class AdaptadorBaseLectura implements PuertoBaseLectura {

    public static final String ID_LECTURA = "lectura";

    private final JdbcTemplate jdbc;

    @Autowired
    public AdaptadorBaseLectura(DataSource dataSource, ReportingProperties props) {
        this(new JdbcTemplate(dataSource));
        this.jdbc.setQueryTimeout((int) Math.max(1, props.timeout().toSeconds()));
    }

    AdaptadorBaseLectura(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SolicitudConsulta consulta(PeriodoConsulta periodo) {
        return new SolicitudConsulta(Fuente.BASE_LECTURA, ID_LECTURA, this::leer);
    }

    DatosBaseLectura leer() {
        OffsetDateTime ultimo = jdbc.queryForObject("SELECT max(occurred_at) FROM eventos_procesados",
                OffsetDateTime.class);
        return new DatosBaseLectura(ultimo);
    }
}
