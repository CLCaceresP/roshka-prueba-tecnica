package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.roshka.challenge.pedidos.aplicacion.idempotencia.ClaveIdempotenciaAusente;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.ClaveIdempotenciaEnProceso;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.ClaveIdempotenciaInvalida;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.ClaveIdempotenciaReutilizada;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.ConfiguracionIdempotencia;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.ModificacionConcurrente;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoNoDisponible;
import com.roshka.challenge.pedidos.dominio.LineaProductoDuplicada;
import com.roshka.challenge.pedidos.dominio.ParametroInvalido;
import com.roshka.challenge.pedidos.dominio.PedidoInvalido;
import com.roshka.challenge.pedidos.dominio.PedidoNoCancelable;
import com.roshka.challenge.pedidos.dominio.PedidoNoEncontrado;
import java.time.Duration;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Problem details de RFC 9457; la propiedad {@code code} es el código de error estable y
 * documentado.
 */
@RestControllerAdvice
class ErroresApi extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ErroresApi.class);

  static final String VALIDACION_FALLIDA = "VALIDATION_FAILED";
  static final String MODIFICACION_CONCURRENTE = "CONCURRENT_MODIFICATION";

  private final Duration reintentarDespues;

  ErroresApi(ConfiguracionIdempotencia ajustes) {
    this.reintentarDespues = ajustes.reintentarDespues();
  }

  @ExceptionHandler(ParametroInvalido.class)
  ProblemDetail parametroInvalido(ParametroInvalido e) {
    return problema(HttpStatus.BAD_REQUEST, VALIDACION_FALLIDA, e.getMessage());
  }

  @ExceptionHandler(PedidoNoEncontrado.class)
  ProblemDetail pedidoNoEncontrado(PedidoNoEncontrado e) {
    return problema(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(LineaProductoDuplicada.class)
  ProblemDetail lineaDuplicada(LineaProductoDuplicada e) {
    return problema(HttpStatus.BAD_REQUEST, "DUPLICATE_PRODUCT_LINE", e.getMessage());
  }

  @ExceptionHandler(PedidoInvalido.class)
  ProblemDetail pedidoInvalido(PedidoInvalido e) {
    return problema(HttpStatus.BAD_REQUEST, VALIDACION_FALLIDA, e.getMessage());
  }

  @ExceptionHandler(PedidoNoCancelable.class)
  ProblemDetail noCancelable(PedidoNoCancelable e) {
    return problema(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", e.getMessage());
  }

  @ExceptionHandler(ModificacionConcurrente.class)
  ResponseEntity<ProblemDetail> modificacionConcurrente(ModificacionConcurrente e) {
    return conflictoReintentarDespues(
        MODIFICACION_CONCURRENTE, e.getMessage(), e.reintentarDespues());
  }

  /** El bloqueo optimista detectó un cambio superpuesto que se escapó al bloqueo del pedido. */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> conflictoOptimista(ObjectOptimisticLockingFailureException e) {
    return conflictoReintentarDespues(
        MODIFICACION_CONCURRENTE, "El pedido se modificó concurrentemente", reintentarDespues);
  }

  @ExceptionHandler(ClaveIdempotenciaAusente.class)
  ProblemDetail claveAusente(ClaveIdempotenciaAusente e) {
    return problema(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_MISSING", e.getMessage());
  }

  @ExceptionHandler(ClaveIdempotenciaInvalida.class)
  ProblemDetail claveInvalida(ClaveIdempotenciaInvalida e) {
    return problema(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", e.getMessage());
  }

  @ExceptionHandler(ClaveIdempotenciaReutilizada.class)
  ProblemDetail claveReutilizada(ClaveIdempotenciaReutilizada e) {
    return problema(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", e.getMessage());
  }

  @ExceptionHandler(ClaveIdempotenciaEnProceso.class)
  ResponseEntity<ProblemDetail> claveEnProceso(ClaveIdempotenciaEnProceso e) {
    return conflictoReintentarDespues(
        "IDEMPOTENCY_KEY_IN_PROGRESS", e.getMessage(), e.reintentarDespues());
  }

  @ExceptionHandler(BloqueoNoDisponible.class)
  ProblemDetail bloqueoNoDisponible(BloqueoNoDisponible e) {
    return problema(HttpStatus.SERVICE_UNAVAILABLE, "LOCK_UNAVAILABLE", e.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders cabeceras,
      HttpStatusCode estado,
      WebRequest solicitud) {
    var objetivo = ex.getBindingResult().getTarget();
    var tipo = objetivo == null ? null : objetivo.getClass();
    var detalle =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> NombresJson.de(tipo, error.getField()) + " " + error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
    return solicitudInvalida(detalle);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders cabeceras,
      HttpStatusCode estado,
      WebRequest solicitud) {
    for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
      if (causa instanceof UnrecognizedPropertyException desconocida) {
        var permitidas =
            desconocida.getKnownPropertyIds() == null
                ? ""
                : desconocida.getKnownPropertyIds().stream()
                    .map(String::valueOf)
                    .sorted()
                    .collect(Collectors.joining(", "));
        return solicitudInvalida(
            "Propiedad desconocida «%s»; las permitidas son: %s"
                .formatted(desconocida.getPropertyName(), permitidas));
      }
    }
    return solicitudInvalida("El cuerpo de la solicitud falta o no es un JSON válido");
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex,
      HttpHeaders cabeceras,
      HttpStatusCode estado,
      WebRequest solicitud) {
    return solicitudInvalida("El parámetro " + ex.getPropertyName() + " tiene un formato inválido");
  }

  private static ResponseEntity<ProblemDetail> conflictoReintentarDespues(
      String codigo, String detalle, Duration reintentarDespues) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, reintentarDespues.toSeconds())))
        .body(problema(HttpStatus.CONFLICT, codigo, detalle));
  }

  private static ResponseEntity<Object> solicitudInvalida(String detalle) {
    return ResponseEntity.badRequest()
        .body(problema(HttpStatus.BAD_REQUEST, VALIDACION_FALLIDA, detalle));
  }

  private static ProblemDetail problema(HttpStatus estado, String codigo, String detalle) {
    log.atLevel(estado.is5xxServerError() ? Level.ERROR : Level.WARN)
        .addKeyValue("status", estado.value())
        .addKeyValue("code", codigo)
        .log("Respuesta de error %d %s: %s".formatted(estado.value(), codigo, detalle));
    var problema = ProblemDetail.forStatusAndDetail(estado, detalle);
    problema.setTitle(estado.getReasonPhrase());
    problema.setProperty("code", codigo);
    return problema;
  }
}
