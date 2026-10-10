package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import com.roshka.challenge.pedidos.aplicacion.idempotencia.SolicitudIdempotente;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.CancelarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ColocarPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ListarPedidos;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ObtenerPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.SolicitudesIdempotentes;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@Tag(name = "Pedidos", description = "Operaciones sobre pedidos.")
class PedidoController {

  private final ColocarPedido colocarPedido;
  private final ObtenerPedido obtenerPedido;
  private final ListarPedidos listarPedidos;
  private final CancelarPedido cancelarPedido;
  private final SolicitudesIdempotentes solicitudesIdempotentes;
  private final HttpIdempotente http;
  private final HuellaSolicitud huella;

  PedidoController(
      ColocarPedido colocarPedido,
      ObtenerPedido obtenerPedido,
      ListarPedidos listarPedidos,
      CancelarPedido cancelarPedido,
      SolicitudesIdempotentes solicitudesIdempotentes,
      HttpIdempotente http,
      HuellaSolicitud huella) {
    this.colocarPedido = colocarPedido;
    this.obtenerPedido = obtenerPedido;
    this.listarPedidos = listarPedidos;
    this.cancelarPedido = cancelarPedido;
    this.solicitudesIdempotentes = solicitudesIdempotentes;
    this.http = http;
    this.huella = huella;
  }

  @PostMapping
  @Operation(
      tags = "Pedidos",
      summary = "Crear un pedido",
      description =
          """
          Registra el pedido en estado PENDING y responde 202. La reserva de stock se resuelve \
          después, así que el resultado se ve consultando el pedido. El id lo genera el servidor.""")
  @ApiResponse(
      responseCode = "202",
      description = "Pedido recibido.",
      headers =
          @Header(
              name = "Location",
              description = "Ruta del pedido creado.",
              schema = @Schema(type = "string", example = "/orders/12670fca")),
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = PedidoResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Datos inválidos o problema con el Idempotency-Key.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION),
                @ExampleObject(
                    name = "DUPLICATE_PRODUCT_LINE",
                    value = Documentacion.LINEA_DUPLICADA),
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_MISSING",
                    value = Documentacion.CLAVE_AUSENTE),
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_INVALID",
                    value = Documentacion.CLAVE_INVALIDA)
              }))
  @ApiResponse(
      responseCode = "409",
      description = "Ya hay una solicitud en proceso con la misma clave.",
      headers =
          @Header(
              name = "Retry-After",
              description = Documentacion.REINTENTAR_DESPUES,
              schema = @Schema(type = "integer", example = "1")),
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_IN_PROGRESS",
                    value = Documentacion.CLAVE_EN_PROCESO)
              }))
  @ApiResponse(
      responseCode = "422",
      description = "La clave ya se usó con otro cuerpo.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_REUSED",
                    value = Documentacion.CLAVE_REUTILIZADA)
              }))
  @ApiResponse(
      responseCode = "503",
      description = "Redis no está disponible.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(
                    name = "LOCK_UNAVAILABLE",
                    value = Documentacion.BLOQUEO_NO_DISPONIBLE)
              }))
  ResponseEntity<String> colocar(
      @Parameter(
              name = HttpIdempotente.CABECERA_CLAVE,
              in = ParameterIn.HEADER,
              required = true,
              description = Documentacion.CLAVE_IDEMPOTENCIA,
              schema =
                  @Schema(
                      type = "string",
                      pattern = Documentacion.FORMATO_CLAVE,
                      example = Documentacion.EJEMPLO_CLAVE))
          @RequestHeader(name = HttpIdempotente.CABECERA_CLAVE, required = false)
          String claveIdempotencia,
      @RequestAttribute(IdCorrelacionFilter.ATRIBUTO) String idCorrelacion,
      @Valid @RequestBody ColocarPedidoRequest cuerpo) {
    var solicitud =
        new SolicitudIdempotente(claveIdempotencia, huella.de("POST", "/orders", cuerpo));
    var lineas =
        cuerpo.lineas().stream()
            .map(linea -> new LineaPedido(linea.idProducto(), linea.cantidad()))
            .toList();
    var resultado =
        solicitudesIdempotentes.ejecutar(
            solicitud,
            List.of(),
            () -> {
              var pedido = colocarPedido.colocar(lineas, idCorrelacion);
              return http.registro(
                  HttpStatus.ACCEPTED.value(),
                  Map.of("Location", "/orders/" + pedido.id()),
                  PedidoResponse.desde(pedido));
            });
    return http.aHttp(resultado);
  }

  @GetMapping
  @Operation(
      tags = "Pedidos",
      summary = "Listar pedidos",
      description =
          """
          Devuelve los pedidos creados en el rango de fechas, del más reciente al más antiguo. \
          Si no se indican fechas devuelve los de hoy.""")
  @ApiResponse(responseCode = "200", description = "Página de pedidos.")
  @ApiResponse(
      responseCode = "400",
      description = "Fechas o paginación inválidas.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION)
              }))
  PaginaResponse<PedidoResponse> listar(
      @Parameter(description = Documentacion.DESDE, example = Documentacion.EJEMPLO_FECHA)
          @RequestParam(name = "from", required = false)
          String desde,
      @Parameter(description = Documentacion.HASTA, example = Documentacion.EJEMPLO_FECHA)
          @RequestParam(name = "to", required = false)
          String hasta,
      @Parameter(
              description = Documentacion.PAGINA,
              schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
          @RequestParam(name = "page", required = false)
          Integer pagina,
      @Parameter(
              description = Documentacion.TAMANIO,
              schema =
                  @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
          @RequestParam(name = "size", required = false)
          Integer tamanio) {
    var pedidos =
        listarPedidos.listar(
            FechaHttp.parsear("from", desde), FechaHttp.parsear("to", hasta), pagina, tamanio);
    return PaginaResponse.desde(pedidos, PedidoResponse::desde);
  }

  @GetMapping("/{orderId}")
  @Operation(
      tags = "Pedidos",
      summary = "Obtener un pedido",
      description = "Devuelve el estado del pedido y sus líneas.")
  @ApiResponse(responseCode = "200", description = "Pedido encontrado.")
  @ApiResponse(
      responseCode = "400",
      description = "El id no tiene formato UUID.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION)
              }))
  @ApiResponse(
      responseCode = "404",
      description = "El pedido no existe.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(name = "ORDER_NOT_FOUND", value = Documentacion.PEDIDO_NO_ENCONTRADO)
              }))
  PedidoResponse get(
      @Parameter(description = "Id del pedido.") @PathVariable("orderId") UUID idPedido) {
    return PedidoResponse.desde(obtenerPedido.get(idPedido));
  }

  @DeleteMapping("/{orderId}")
  @Operation(
      tags = "Pedidos",
      summary = "Cancelar un pedido",
      description =
          """
          Cancela un pedido PENDING o CONFIRMED y libera su stock. Si ya estaba cancelado devuelve \
          el mismo resultado. Un pedido REJECTED no se puede cancelar.""")
  @ApiResponse(
      responseCode = "200",
      description = "Pedido cancelado.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = PedidoResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Id o Idempotency-Key inválidos.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION),
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_MISSING",
                    value = Documentacion.CLAVE_AUSENTE),
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_INVALID",
                    value = Documentacion.CLAVE_INVALIDA)
              }))
  @ApiResponse(
      responseCode = "404",
      description = "El pedido no existe.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(name = "ORDER_NOT_FOUND", value = Documentacion.PEDIDO_NO_ENCONTRADO)
              }))
  @ApiResponse(
      responseCode = "409",
      description =
          "El pedido no se puede cancelar, se está modificando o la clave ya está en proceso.",
      headers =
          @Header(
              name = "Retry-After",
              description = Documentacion.REINTENTAR_DESPUES,
              schema = @Schema(type = "integer", example = "1")),
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(name = "ORDER_NOT_CANCELLABLE", value = Documentacion.NO_CANCELABLE),
                @ExampleObject(
                    name = "CONCURRENT_MODIFICATION",
                    value = Documentacion.MODIFICACION_CONCURRENTE),
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_IN_PROGRESS",
                    value = Documentacion.CLAVE_EN_PROCESO)
              }))
  @ApiResponse(
      responseCode = "422",
      description = "La clave ya se usó con otro cuerpo.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_REUSED",
                    value = Documentacion.CLAVE_REUTILIZADA)
              }))
  @ApiResponse(
      responseCode = "503",
      description = "Redis no está disponible.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(
                    name = "LOCK_UNAVAILABLE",
                    value = Documentacion.BLOQUEO_NO_DISPONIBLE)
              }))
  ResponseEntity<String> cancelar(
      @Parameter(
              name = HttpIdempotente.CABECERA_CLAVE,
              in = ParameterIn.HEADER,
              required = true,
              description = Documentacion.CLAVE_IDEMPOTENCIA,
              schema =
                  @Schema(
                      type = "string",
                      pattern = Documentacion.FORMATO_CLAVE,
                      example = Documentacion.EJEMPLO_CLAVE))
          @RequestHeader(name = HttpIdempotente.CABECERA_CLAVE, required = false)
          String claveIdempotencia,
      @RequestAttribute(IdCorrelacionFilter.ATRIBUTO) String idCorrelacion,
      @Parameter(description = "Id del pedido.") @PathVariable("orderId") UUID idPedido) {
    var ruta = "/orders/" + idPedido;
    var solicitud = new SolicitudIdempotente(claveIdempotencia, huella.de("DELETE", ruta, null));
    var resultado =
        solicitudesIdempotentes.ejecutarEnPedido(
            solicitud,
            idPedido,
            () ->
                http.registro(
                    HttpStatus.OK.value(),
                    Map.of(),
                    PedidoResponse.desde(cancelarPedido.cancelar(idPedido, idCorrelacion))));
    return http.aHttp(resultado);
  }
}
