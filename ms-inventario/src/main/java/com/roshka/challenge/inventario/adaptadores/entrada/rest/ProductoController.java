package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import com.roshka.challenge.inventario.aplicacion.idempotencia.SolicitudIdempotente;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.AjustarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ListarMovimientos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ListarProductos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ObtenerStockProducto;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.SolicitudesIdempotentes;
import com.roshka.challenge.inventario.dominio.AjusteStock;
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
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@Tag(name = "Productos", description = "Operaciones sobre productos.")
@Tag(name = "Stock", description = "Consulta y ajuste del stock.")
class ProductoController {

  private final RegistrarProducto registrarProducto;
  private final ObtenerStockProducto obtenerStockProducto;
  private final ListarProductos listarProductos;
  private final AjustarStock ajustarStock;
  private final ListarMovimientos listarMovimientos;
  private final SolicitudesIdempotentes solicitudesIdempotentes;
  private final HttpIdempotente http;
  private final HuellaSolicitud huella;

  ProductoController(
      RegistrarProducto registrarProducto,
      ObtenerStockProducto obtenerStockProducto,
      ListarProductos listarProductos,
      AjustarStock ajustarStock,
      ListarMovimientos listarMovimientos,
      SolicitudesIdempotentes solicitudesIdempotentes,
      HttpIdempotente http,
      HuellaSolicitud huella) {
    this.registrarProducto = registrarProducto;
    this.obtenerStockProducto = obtenerStockProducto;
    this.listarProductos = listarProductos;
    this.ajustarStock = ajustarStock;
    this.listarMovimientos = listarMovimientos;
    this.solicitudesIdempotentes = solicitudesIdempotentes;
    this.http = http;
    this.huella = huella;
  }

  @PostMapping
  @Operation(
      tags = "Productos",
      summary = "Crear un producto",
      description =
          "Crea un producto con su stock inicial. El id lo genera el servidor. El nombre no puede repetirse (se ignoran mayúsculas y espacios de más).")
  @ApiResponse(
      responseCode = "201",
      description = "Producto creado.",
      headers =
          @Header(
              name = "Location",
              description = "Ruta del producto creado.",
              schema = @Schema(type = "string", example = "/products/6805e54f")),
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProductoResponse.class)))
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
                    name = "IDEMPOTENCY_KEY_MISSING",
                    value = Documentacion.CLAVE_AUSENTE),
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_INVALID",
                    value = Documentacion.CLAVE_INVALIDA)
              }))
  @ApiResponse(
      responseCode = "409",
      description = "El nombre ya existe o hay una solicitud en proceso con la misma clave.",
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
                    name = "PRODUCT_ALREADY_EXISTS",
                    value = Documentacion.PRODUCTO_REPETIDO),
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
              examples =
                  @ExampleObject(
                      name = "IDEMPOTENCY_KEY_REUSED",
                      value = Documentacion.CLAVE_REUTILIZADA)))
  @ApiResponse(
      responseCode = "503",
      description = "Redis no está disponible.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(
                      name = "LOCK_UNAVAILABLE",
                      value = Documentacion.BLOQUEO_NO_DISPONIBLE)))
  ResponseEntity<String> registrar(
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
      @Valid @RequestBody RegistrarProductoRequest cuerpo) {
    var solicitud =
        new SolicitudIdempotente(claveIdempotencia, huella.de("POST", "/products", cuerpo));
    var resultado =
        solicitudesIdempotentes.ejecutar(
            solicitud,
            () -> {
              var producto = registrarProducto.registrar(cuerpo.nombre(), cuerpo.cantidadInicial());
              return http.registro(
                  HttpStatus.CREATED.value(),
                  Map.of("Location", "/products/" + producto.id()),
                  ProductoResponse.desde(producto));
            });
    return http.aHttp(resultado);
  }

  @GetMapping
  @Operation(
      tags = "Productos",
      summary = "Listar productos",
      description =
          "Devuelve los productos ordenados por nombre, sin distinguir mayúsculas. Una página fuera de rango devuelve la lista vacía.")
  @ApiResponse(responseCode = "200", description = "Página de productos.")
  @ApiResponse(
      responseCode = "400",
      description = "Paginación inválida.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION)))
  PaginaResponse<ProductoResponse> listar(
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
    return PaginaResponse.desde(listarProductos.listar(pagina, tamanio), ProductoResponse::desde);
  }

  @GetMapping("/{productId}")
  @Operation(
      tags = "Productos",
      summary = "Obtener un producto",
      description =
          "Devuelve el nombre y el stock (`total`, `available`, `reserved`) del producto.")
  @ApiResponse(responseCode = "200", description = "Producto encontrado.")
  @ApiResponse(
      responseCode = "400",
      description = "El id no tiene formato UUID.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION)))
  @ApiResponse(
      responseCode = "404",
      description = "El producto no existe.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(
                      name = "PRODUCT_NOT_FOUND",
                      value = Documentacion.PRODUCTO_NO_ENCONTRADO)))
  ProductoResponse obtener(
      @Parameter(description = "Id del producto.") @PathVariable("productId") UUID idProducto) {
    return ProductoResponse.desde(obtenerStockProducto.stockDe(idProducto));
  }

  @PutMapping("/{productId}/stock")
  @Operation(
      tags = "Stock",
      summary = "Fijar el stock disponible",
      description =
          """
          Deja el producto con las unidades disponibles indicadas. Las reservadas no cambian, así \
          que el total pasa a ser disponible + reservado. Si el valor es distinto del actual se \
          registra un movimiento en el historial.""")
  @ApiResponse(
      responseCode = "200",
      description = "Stock actualizado.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = StockActualizadoResponse.class)))
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
                    name = "IDEMPOTENCY_KEY_MISSING",
                    value = Documentacion.CLAVE_AUSENTE),
                @ExampleObject(
                    name = "IDEMPOTENCY_KEY_INVALID",
                    value = Documentacion.CLAVE_INVALIDA)
              }))
  @ApiResponse(
      responseCode = "404",
      description = "El producto no existe.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples = {
                @ExampleObject(
                    name = "PRODUCT_NOT_FOUND",
                    value = Documentacion.PRODUCTO_NO_ENCONTRADO)
              }))
  @ApiResponse(
      responseCode = "409",
      description = "El total superaría el máximo permitido o la clave ya está en proceso.",
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
                    name = "STOCK_LIMIT_EXCEEDED",
                    value = Documentacion.LIMITE_DE_STOCK),
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
  ResponseEntity<String> fijarStock(
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
      @Parameter(description = "Id del producto.") @PathVariable("productId") UUID idProducto,
      @Valid @RequestBody FijarStockRequest cuerpo) {
    var ruta = "/products/" + idProducto + "/stock";
    var solicitud = new SolicitudIdempotente(claveIdempotencia, huella.de("PUT", ruta, cuerpo));
    var resultado =
        solicitudesIdempotentes.ejecutar(
            solicitud,
            () -> {
              var ajuste = AjusteStock.de(idProducto, cuerpo.disponible(), cuerpo.motivo());
              return http.registro(
                  HttpStatus.OK.value(),
                  Map.of(),
                  StockActualizadoResponse.desde(ajustarStock.ajustar(ajuste)));
            });
    return http.aHttp(resultado);
  }

  @GetMapping("/{productId}/stock/movements")
  @Operation(
      tags = "Stock",
      summary = "Historial de ajustes",
      description =
          "Lista los ajustes de stock del producto, del más reciente al más antiguo. El stock inicial no aparece.")
  @ApiResponse(responseCode = "200", description = "Página de ajustes.")
  @ApiResponse(
      responseCode = "400",
      description = "Paginación o id inválidos.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION)))
  @ApiResponse(
      responseCode = "404",
      description = "El producto no existe.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(
                      name = "PRODUCT_NOT_FOUND",
                      value = Documentacion.PRODUCTO_NO_ENCONTRADO)))
  PaginaResponse<MovimientoResponse> movimientos(
      @Parameter(description = "Id del producto.") @PathVariable("productId") UUID idProducto,
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
    return PaginaResponse.desde(
        listarMovimientos.listar(idProducto, pagina, tamanio), MovimientoResponse::desde);
  }

  @GetMapping("/{productId}/stock")
  @Operation(
      tags = "Stock",
      summary = "Obtener el stock de un producto",
      description = "Devuelve el total, lo disponible y lo reservado.")
  @ApiResponse(responseCode = "200", description = "Stock del producto.")
  @ApiResponse(
      responseCode = "400",
      description = "El id no tiene formato UUID.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(name = "VALIDATION_FAILED", value = Documentacion.VALIDACION)))
  @ApiResponse(
      responseCode = "404",
      description = "El producto no existe.",
      content =
          @Content(
              mediaType = Documentacion.TIPO_PROBLEMA,
              schema = @Schema(implementation = ProblemaResponse.class),
              examples =
                  @ExampleObject(
                      name = "PRODUCT_NOT_FOUND",
                      value = Documentacion.PRODUCTO_NO_ENCONTRADO)))
  StockResponse stock(
      @Parameter(description = "Id del producto.") @PathVariable("productId") UUID idProducto) {
    return StockResponse.desde(obtenerStockProducto.stockDe(idProducto));
  }
}
