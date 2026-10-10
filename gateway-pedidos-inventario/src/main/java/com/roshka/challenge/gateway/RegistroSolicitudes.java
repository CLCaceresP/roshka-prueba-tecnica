package com.roshka.challenge.gateway;

import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Escribe una línea por solicitud que pasa por el gateway (spec 008, RN-01). El contexto reactivo
 * no mantiene el MDC, así que la correlación y la traza van como atributos de la línea; la traza se
 * toma al entrar del contexto de la observación HTTP de Spring.
 */
final class RegistroSolicitudes implements GlobalFilter, Ordered {

  static final String MENSAJE_RECIBIDA = "Solicitud HTTP recibida";
  static final String MENSAJE = "Solicitud HTTP procesada";
  private static final Logger log = LoggerFactory.getLogger(RegistroSolicitudes.class);
  private static final List<String> RUTAS_DE_SONDA = List.of("/actuator");

  @Override
  public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
    var inicio = System.nanoTime();
    var span =
        ServerRequestObservationContext.findCurrent(intercambio.getAttributes())
            .map(contexto -> contexto.<TracingContext>get(TracingContext.class))
            .map(TracingContext::getSpan)
            .orElse(null);
    var idTraza = span == null ? null : span.context().traceId();
    var idSpan = span == null ? null : span.context().spanId();
    registrarEntrada(intercambio, idTraza, idSpan);
    return cadena
        .filter(intercambio)
        .doFinally(
            senal -> registrar(intercambio, System.nanoTime() - inicio, idTraza, idSpan, senal));
  }

  private void registrarEntrada(ServerWebExchange intercambio, String idTraza, String idSpan) {
    var solicitud = intercambio.getRequest();
    var ruta = solicitud.getPath().value();
    var nivel = nivel(ruta, 200);
    if (!log.isEnabledForLevel(nivel)) {
      return;
    }
    var evento =
        log.atLevel(nivel)
            .addKeyValue("method", solicitud.getMethod().name())
            .addKeyValue("path", ruta)
            .addKeyValue("host", solicitud.getHeaders().getFirst("Host"));
    var correlacion = solicitud.getHeaders().getFirst(FiltrosEntrada.CABECERA_CORRELACION);
    if (correlacion != null) {
      evento = evento.addKeyValue("correlationId", correlacion);
    }
    var cliente = solicitud.getRemoteAddress();
    if (cliente != null) {
      evento = evento.addKeyValue("clientIp", direccion(cliente));
    }
    var agente = solicitud.getHeaders().getFirst("User-Agent");
    if (agente != null) {
      evento = evento.addKeyValue("userAgent", agente);
    }
    if (idTraza != null) {
      evento = evento.addKeyValue("traceId", idTraza).addKeyValue("spanId", idSpan);
    }
    evento.log(MENSAJE_RECIBIDA + ": " + solicitud.getMethod().name() + " " + ruta);
  }

  private void registrar(
      ServerWebExchange intercambio, long nanos, String idTraza, String idSpan, SignalType senal) {
    var solicitud = intercambio.getRequest();
    var ruta = solicitud.getPath().value();
    var codigo = intercambio.getResponse().getStatusCode();
    var estado = senal == SignalType.ON_ERROR || codigo == null ? 500 : codigo.value();
    var nivel = nivel(ruta, estado);
    if (!log.isEnabledForLevel(nivel)) {
      return;
    }
    var ms = Duration.ofNanos(nanos).toMillis();
    var evento =
        log.atLevel(nivel)
            .addKeyValue("method", solicitud.getMethod().name())
            .addKeyValue("path", ruta)
            .addKeyValue("status", estado)
            .addKeyValue("durationMs", ms)
            .addKeyValue("host", solicitud.getHeaders().getFirst("Host"));
    var correlacion = solicitud.getHeaders().getFirst(FiltrosEntrada.CABECERA_CORRELACION);
    if (correlacion != null) {
      evento = evento.addKeyValue("correlationId", correlacion);
    }
    var cliente = solicitud.getRemoteAddress();
    if (cliente != null) {
      evento = evento.addKeyValue("clientIp", direccion(cliente));
    }
    var agente = solicitud.getHeaders().getFirst("User-Agent");
    if (agente != null) {
      evento = evento.addKeyValue("userAgent", agente);
    }
    var mensaje =
        "%s: %s %s respondió %d en %d ms"
            .formatted(MENSAJE, solicitud.getMethod().name(), ruta, estado, ms);
    Route destino = intercambio.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    if (destino != null) {
      evento = evento.addKeyValue("routeId", destino.getId());
    }
    var url = intercambio.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
    if (url != null) {
      evento = evento.addKeyValue("destination", url.toString());
    }
    if (destino != null && url != null) {
      mensaje += " (ruta %s → %s)".formatted(destino.getId(), url);
    }
    var motivo = motivoDelGateway(estado);
    if (motivo != null) {
      mensaje += ": " + motivo;
    }
    if (idTraza != null) {
      evento = evento.addKeyValue("traceId", idTraza).addKeyValue("spanId", idSpan);
    }
    evento.log(mensaje);
  }

  private static String motivoDelGateway(int estado) {
    return switch (estado) {
      case 429 -> "rechazada por el límite de solicitudes por cliente, no llegó al servicio";
      case 502, 503, 504 -> "el servicio de destino no respondió a tiempo o no estaba disponible";
      default -> null;
    };
  }

  private static String direccion(InetSocketAddress cliente) {
    return cliente.getAddress() != null
        ? cliente.getAddress().getHostAddress()
        : cliente.getHostString();
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

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
