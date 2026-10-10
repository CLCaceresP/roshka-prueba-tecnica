package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

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
 * Conserva el X-Correlation-Id del cliente (o genera uno) y lo expone a los controllers, a los logs
 * y a la respuesta. La saga lo transporta sin cambios en cada mensaje (spec 006, RN-05).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdCorrelacionFilter extends OncePerRequestFilter {

  static final String CABECERA = "X-Correlation-Id";
  static final String ATRIBUTO = "correlationId";
  private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9_.:-]{1,100}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest solicitud, HttpServletResponse respuestaHttp, FilterChain cadena)
      throws ServletException, IOException {
    var recibido = solicitud.getHeader(CABECERA);
    var idCorrelacion =
        recibido != null && VALIDO.matcher(recibido).matches()
            ? recibido
            : UUID.randomUUID().toString();
    solicitud.setAttribute(ATRIBUTO, idCorrelacion);
    respuestaHttp.setHeader(CABECERA, idCorrelacion);
    try (var ignorado = MDC.putCloseable(ATRIBUTO, idCorrelacion)) {
      cadena.doFilter(solicitud, respuestaHttp);
    }
  }
}
