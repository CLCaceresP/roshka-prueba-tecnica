package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Escribe una línea por solicitud HTTP al terminar (spec 008, RN-01). Corre dentro del filtro de
 * correlación y de la observación HTTP de Spring, así que la línea lleva el {@code correlationId}
 * del MDC y la traza activa. No registra cuerpos ni headers completos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class RegistroSolicitudesFilter extends OncePerRequestFilter {

  static final String MENSAJE_RECIBIDA = "Solicitud HTTP recibida";
  static final String MENSAJE = "Solicitud HTTP procesada";
  private static final Logger log = LoggerFactory.getLogger(RegistroSolicitudesFilter.class);
  private static final List<String> RUTAS_DE_SONDA =
      List.of("/actuator", "/swagger-ui", "/v3/api-docs");

  @Override
  protected void doFilterInternal(
      HttpServletRequest solicitud, HttpServletResponse respuestaHttp, FilterChain cadena)
      throws ServletException, IOException {
    var inicio = System.nanoTime();
    registrarEntrada(solicitud);
    var fallo = false;
    try {
      cadena.doFilter(solicitud, respuestaHttp);
    } catch (ServletException | IOException | RuntimeException e) {
      fallo = true;
      throw e;
    } finally {
      registrar(
          solicitud,
          respuestaHttp,
          fallo ? 500 : respuestaHttp.getStatus(),
          System.nanoTime() - inicio);
    }
  }

  private void registrarEntrada(HttpServletRequest solicitud) {
    var uri = solicitud.getRequestURI();
    var nivel = nivel(uri, 200);
    if (!log.isEnabledForLevel(nivel)) {
      return;
    }
    var evento =
        log.atLevel(nivel)
            .addKeyValue("method", solicitud.getMethod())
            .addKeyValue("path", uri)
            .addKeyValue("clientIp", solicitud.getRemoteAddr())
            .addKeyValue("host", solicitud.getHeader("Host"));
    var agente = solicitud.getHeader("User-Agent");
    if (agente != null) {
      evento = evento.addKeyValue("userAgent", agente);
    }
    evento.log(MENSAJE_RECIBIDA + ": " + solicitud.getMethod() + " " + uri);
  }

  private void registrar(
      HttpServletRequest solicitud, HttpServletResponse respuestaHttp, int estado, long nanos) {
    var ruta = rutaDe(solicitud);
    var evento = log.atLevel(nivel(ruta, estado));
    if (!log.isEnabledForLevel(nivel(ruta, estado))) {
      return;
    }
    var ms = Duration.ofNanos(nanos).toMillis();
    evento =
        evento
            .addKeyValue("method", solicitud.getMethod())
            .addKeyValue("path", ruta)
            .addKeyValue("status", estado)
            .addKeyValue("durationMs", ms)
            .addKeyValue("clientIp", solicitud.getRemoteAddr())
            .addKeyValue("host", solicitud.getHeader("Host"));
    var agente = solicitud.getHeader("User-Agent");
    if (agente != null) {
      evento = evento.addKeyValue("userAgent", agente);
    }
    var mensaje =
        "%s: %s %s respondió %d en %d ms"
            .formatted(MENSAJE, solicitud.getMethod(), solicitud.getRequestURI(), estado, ms);
    var ubicacion = respuestaHttp.getHeader("Location");
    if (ubicacion != null) {
      evento = evento.addKeyValue("location", ubicacion);
      mensaje += " (Location: " + ubicacion + ")";
    }
    evento.log(mensaje);
  }

  private static String rutaDe(HttpServletRequest solicitud) {
    var plantilla = solicitud.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return plantilla != null ? plantilla.toString() : solicitud.getRequestURI();
  }

  private static Level nivel(String ruta, int estado) {
    if (RUTAS_DE_SONDA.stream().anyMatch(ruta::startsWith)) {
      return Level.DEBUG;
    }
    if (estado >= 500) {
      return Level.ERROR;
    }
    return estado >= 400 ? Level.WARN : Level.INFO;
  }
}
