package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.roshka.challenge.inventario.aplicacion.idempotencia.ClaveIdempotenciaAusente;
import com.roshka.challenge.inventario.aplicacion.idempotencia.ClaveIdempotenciaEnProceso;
import com.roshka.challenge.inventario.aplicacion.idempotencia.ClaveIdempotenciaInvalida;
import com.roshka.challenge.inventario.aplicacion.idempotencia.ClaveIdempotenciaReutilizada;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.BloqueoNoDisponible;
import com.roshka.challenge.inventario.dominio.LimiteStockExcedido;
import com.roshka.challenge.inventario.dominio.ParametroInvalido;
import com.roshka.challenge.inventario.dominio.ProductoDuplicado;
import com.roshka.challenge.inventario.dominio.ProductoInvalido;
import com.roshka.challenge.inventario.dominio.ProductoNoEncontrado;
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

  @ExceptionHandler(ProductoNoEncontrado.class)
  ProblemDetail productoNoEncontrado(ProductoNoEncontrado e) {
    return problema(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(ProductoInvalido.class)
  ProblemDetail productoInvalido(ProductoInvalido e) {
    return problema(HttpStatus.BAD_REQUEST, VALIDACION_FALLIDA, e.getMessage());
  }

  @ExceptionHandler(LimiteStockExcedido.class)
  ProblemDetail limiteStockExcedido(LimiteStockExcedido e) {
    return problema(HttpStatus.CONFLICT, "STOCK_LIMIT_EXCEEDED", e.getMessage());
  }

  @ExceptionHandler(ParametroInvalido.class)
  ProblemDetail parametroInvalido(ParametroInvalido e) {
    return problema(HttpStatus.BAD_REQUEST, VALIDACION_FALLIDA, e.getMessage());
  }

  @ExceptionHandler(ProductoDuplicado.class)
  ProblemDetail productoDuplicado(ProductoDuplicado e) {
    return problema(HttpStatus.CONFLICT, "PRODUCT_ALREADY_EXISTS", e.getMessage());
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
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header(
            HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.reintentarDespues().toSeconds())))
        .body(problema(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_IN_PROGRESS", e.getMessage()));
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
