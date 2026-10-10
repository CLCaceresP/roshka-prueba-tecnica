package com.roshka.challenge.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Lo único que el gateway hace con las solicitudes (ADR 0007): id de correlación y una verificación
 * temprana de que las escrituras traen Idempotency-Key. La idempotencia sigue siendo
 * responsabilidad de los servicios.
 */
final class FiltrosEntrada {

  static final String CABECERA_CORRELACION = "X-Correlation-Id";
  static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";
  private static final Logger log = LoggerFactory.getLogger(FiltrosEntrada.class);

  private FiltrosEntrada() {}

  /** Conserva el id de correlación del cliente o genera uno, lo reenvía y lo devuelve. */
  static final class IdCorrelacion implements GlobalFilter, Ordered {

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9_.:-]{1,100}");

    @Override
    public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
      var recibido = intercambio.getRequest().getHeaders().getFirst(CABECERA_CORRELACION);
      var idCorrelacion =
          recibido != null && VALIDO.matcher(recibido).matches()
              ? recibido
              : UUID.randomUUID().toString();
      // Se fija al hacer commit para que reemplace el header copiado del servicio en lugar de
      // agregar otro.
      var respuestaHttp = intercambio.getResponse();
      respuestaHttp.beforeCommit(
          () -> {
            respuestaHttp.getHeaders().set(CABECERA_CORRELACION, idCorrelacion);
            return Mono.empty();
          });
      var solicitud =
          intercambio
              .getRequest()
              .mutate()
              .headers(cabeceras -> cabeceras.set(CABECERA_CORRELACION, idCorrelacion))
              .build();
      return cadena.filter(intercambio.mutate().request(solicitud).build());
    }

    @Override
    public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE;
    }
  }

  /**
   * Rechaza las escrituras sin Idempotency-Key con el mismo problem detail que devuelven los
   * servicios.
   */
  static final class PresenciaClaveIdempotencia implements GlobalFilter, Ordered {

    private static final Set<HttpMethod> ESCRITURAS =
        Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private static final byte[] PROBLEMA =
        """
        {"type":"about:blank","title":"Bad Request","status":400,\
        "detail":"Las solicitudes de escritura requieren el header Idempotency-Key",\
        "code":"IDEMPOTENCY_KEY_MISSING"}"""
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
      var solicitud = intercambio.getRequest();
      var clave = solicitud.getHeaders().getFirst(CABECERA_IDEMPOTENCIA);
      if (!ESCRITURAS.contains(solicitud.getMethod()) || (clave != null && !clave.isBlank())) {
        return cadena.filter(intercambio);
      }
      log.atWarn()
          .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
          .addKeyValue("code", "IDEMPOTENCY_KEY_MISSING")
          .addKeyValue("method", solicitud.getMethod().name())
          .addKeyValue("path", solicitud.getPath().value())
          .addKeyValue("correlationId", solicitud.getHeaders().getFirst(CABECERA_CORRELACION))
          .log(
              "Solicitud rechazada por el gateway: %s %s sin el header Idempotency-Key (IDEMPOTENCY_KEY_MISSING)"
                  .formatted(solicitud.getMethod().name(), solicitud.getPath().value()));
      var respuestaHttp = intercambio.getResponse();
      respuestaHttp.setStatusCode(HttpStatus.BAD_REQUEST);
      respuestaHttp.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
      return respuestaHttp.writeWith(Mono.just(respuestaHttp.bufferFactory().wrap(PROBLEMA)));
    }

    @Override
    public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE + 1;
    }
  }
}
