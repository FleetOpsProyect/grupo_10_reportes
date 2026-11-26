package com.fleetops.reporting.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * X-Correlation-Id (HU-01, criterio 7): si la petición lo trae se reutiliza; si no, se genera un UUID v4. Se
 * devuelve en la cabecera de respuesta y queda en el MDC como correlation_id para todos los logs.
 * <p>
 * Solo se acepta un valor corto con letras, números, guiones, puntos o guiones bajos; cualquier otro se
 * reemplaza por uno nuevo para que no se pueda inyectar texto en los logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";
    public static final String ATRIBUTO = FiltroCorrelacion.class.getName();
    public static final String MDC_CLAVE = "correlation_id";
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String recibido = peticion.getHeader(CABECERA);
        String correlationId = recibido != null && VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        peticion.setAttribute(ATRIBUTO, correlationId);
        respuesta.setHeader(CABECERA, correlationId);
        MDC.put(MDC_CLAVE, correlationId);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove(MDC_CLAVE);
        }
    }

    /** correlation_id de la petición en curso. */
    public static String de(HttpServletRequest peticion) {
        Object valor = peticion.getAttribute(ATRIBUTO);
        return valor == null ? MDC.get(MDC_CLAVE) : valor.toString();
    }
}
