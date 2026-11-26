package com.fleetops.reporting.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fleetops.reporting.application.Fuente;
import com.fleetops.reporting.application.PeriodoConsulta;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class AdaptadorBaseLecturaTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdaptadorBaseLectura adaptador = new AdaptadorBaseLectura(jdbc);

    @Test
    void esLaFuenteBaseLecturaYDevuelveElUltimoEventoAplicado() throws Exception {
        OffsetDateTime ultimo = OffsetDateTime.parse("2026-11-26T09:00:00-05:00");
        when(jdbc.queryForObject(anyString(), eq(OffsetDateTime.class))).thenReturn(ultimo);

        var solicitud = adaptador.consulta(PeriodoConsulta.sinRango());

        assertThat(solicitud.fuente()).isEqualTo(Fuente.BASE_LECTURA);
        assertThat(solicitud.accion().call()).isEqualTo(new DatosBaseLectura(ultimo));
    }

    @Test
    void siLaBaseNoRespondeLaLlamadaFalla() {
        when(jdbc.queryForObject(anyString(), eq(OffsetDateTime.class)))
                .thenThrow(new DataAccessResourceFailureException("Neon no responde"));

        var solicitud = adaptador.consulta(PeriodoConsulta.sinRango());

        assertThatThrownBy(() -> solicitud.accion().call()).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
