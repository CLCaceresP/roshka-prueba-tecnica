package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class ProductoApiIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;
  @Autowired RedissonClient redisson;

  @Test
  @DisplayName("REQ-22 · 001-AC-01 crear un producto devuelve 201, Location y su stock")
  void debe_crear_producto_cuando_la_solicitud_es_valida() {
    var nombre = nombreUnico();

    var resultado = registrar(claveNueva(), "{\"name\":\"" + nombre + "\",\"initialQuantity\":10}");

    assertThat(resultado).hasStatus(HttpStatus.CREATED);
    var idProducto = idProductoDe(nombre);
    assertThat(resultado).headers().hasValue("Location", "/products/" + idProducto);
    assertThat(resultado).bodyJson().extractingPath("$.productId").isEqualTo(idProducto.toString());
    assertThat(resultado).bodyJson().extractingPath("$.available").isEqualTo(10);
    assertThat(resultado).bodyJson().extractingPath("$.reserved").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(10);
  }

  @Test
  @DisplayName("REQ-23 · 001-AC-02 el endpoint de stock devuelve disponible, reservado y total")
  void debe_devolver_el_stock_cuando_el_producto_existe() {
    var nombre = nombreUnico();
    registrar(claveNueva(), "{\"name\":\"" + nombre + "\",\"initialQuantity\":7}");

    var resultado = mvc.get().uri("/products/{id}/stock", idProductoDe(nombre)).exchange();

    assertThat(resultado).hasStatus(HttpStatus.OK);
    assertThat(resultado).bodyJson().extractingPath("$.available").isEqualTo(7);
    assertThat(resultado).bodyJson().extractingPath("$.reserved").isEqualTo(0);
    assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(7);
  }

  @Test
  @DisplayName("REQ-23 · 001-AC-03 un producto desconocido es 404 PRODUCT_NOT_FOUND")
  void debe_responder_no_encontrado_cuando_el_producto_no_existe() {
    var resultado = mvc.get().uri("/products/{id}/stock", UUID.randomUUID()).exchange();

    assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
  }

  @Test
  @DisplayName("REQ-23 · un id de producto que no es UUID es 400 VALIDATION_FAILED")
  void debe_responder_solicitud_invalida_cuando_el_id_de_producto_no_es_uuid() {
    var resultado = mvc.get().uri("/products/not-a-uuid/stock").exchange();

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("REQ-22 · 001-AC-11 un cuerpo inválido es 400 VALIDATION_FAILED y no crea nada")
  void debe_rechazar_cuerpo_invalido_sin_crear_nada() {
    var nombre = nombreUnico();

    var negativo = registrar(claveNueva(), "{\"name\":\"" + nombre + "\",\"initialQuantity\":-1}");
    var enBlanco = registrar(claveNueva(), "{\"name\":\"\",\"initialQuantity\":1}");
    var malformado = registrar(claveNueva(), "{\"name\":");

    for (var resultado : new MvcTestResult[] {negativo, enBlanco, malformado}) {
      assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
      assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    }
    assertThat(contarProductosConNombre(nombre)).isZero();
  }

  @Test
  @DisplayName("REQ-24 · 005-AC-01 una escritura sin Idempotency-Key es 400")
  void debe_rechazar_la_escritura_cuando_falta_la_clave_de_idempotencia() {
    var resultado =
        mvc.post()
            .uri("/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + nombreUnico() + "\",\"initialQuantity\":1}")
            .exchange();

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_MISSING");
  }

  @Test
  @DisplayName("REQ-24 · 005 RN-01 una Idempotency-Key malformada es 400")
  void debe_rechazar_la_escritura_cuando_la_clave_de_idempotencia_esta_malformada() {
    var resultado = registrar("not a valid key!", "{\"name\":\"x\",\"initialQuantity\":1}");

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_INVALID");
  }

  @Test
  @DisplayName("REQ-15 REQ-24 · 005-AC-10 un POST repetido reproduce la misma respuesta")
  void debe_reproducir_la_respuesta_identica_cuando_la_solicitud_se_repite_con_la_misma_clave()
      throws Exception {
    var clave = claveNueva();
    var nombre = nombreUnico();
    var cuerpo = "{\"name\":\"" + nombre + "\",\"initialQuantity\":3}";

    var primero = registrar(clave, cuerpo);
    var segundo = registrar(clave, "{ \"initialQuantity\": 3, \"name\": \"" + nombre + "\" }");

    assertThat(segundo).hasStatus(HttpStatus.CREATED);
    assertThat(segundo).headers().hasValue("Idempotent-Replayed", "true");
    assertThat(segundo).headers().hasValue("Location", primero.getResponse().getHeader("Location"));
    assertThat(segundo.getResponse().getContentAsString())
        .isEqualTo(primero.getResponse().getContentAsString());
    assertThat(contarProductosConNombre(nombre)).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-24 · 005-AC-03 la misma clave con otro cuerpo es 422")
  void debe_rechazar_la_clave_reutilizada_cuando_el_cuerpo_difiere() {
    var clave = claveNueva();
    registrar(clave, "{\"name\":\"" + nombreUnico() + "\",\"initialQuantity\":3}");

    var resultado = registrar(clave, "{\"name\":\"" + nombreUnico() + "\",\"initialQuantity\":4}");

    assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  @DisplayName(
      "REQ-24 · 005-AC-08 los errores no se registran: la clave se puede reutilizar tras corregir")
  void debe_aceptar_solicitud_corregida_cuando_el_intento_previo_con_la_misma_clave_fallo() {
    var clave = claveNueva();
    var nombre = nombreUnico();
    registrar(clave, "{\"name\":\"" + nombre + "\",\"initialQuantity\":-5}");

    var resultado = registrar(clave, "{\"name\":\"" + nombre + "\",\"initialQuantity\":5}");

    assertThat(resultado).hasStatus(HttpStatus.CREATED);
    assertThat(contarProductosConNombre(nombre)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "REQ-24 REQ-25 · 005-AC-05 una clave retenida por otra solicitud es 409 con Retry-After")
  void debe_responder_conflicto_con_retry_after_cuando_la_clave_esta_en_proceso() throws Exception {
    var clave = claveNueva();
    var bloqueo = redisson.getLock("idem:ms-inventario:" + clave);
    var hiloOtraSolicitud = 4242L;
    bloqueo.lockAsync(30, TimeUnit.SECONDS, hiloOtraSolicitud).get(5, TimeUnit.SECONDS);
    try {
      var resultado =
          registrar(clave, "{\"name\":\"" + nombreUnico() + "\",\"initialQuantity\":1}");

      assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
      assertThat(resultado).headers().hasValue("Retry-After", "1");
      assertThat(resultado)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("IDEMPOTENCY_KEY_IN_PROGRESS");
    } finally {
      bloqueo.forceUnlock();
    }
  }

  @Test
  @DisplayName("REQ-24 · 005-AC-09 una clave vencida se procesa como nueva")
  void debe_crear_de_nuevo_cuando_la_clave_registrada_expiro() {
    var clave = claveNueva();
    jdbc.sql(
            """
            insert into idempotency_keys
              (key, request_hash, response_code, response_headers, response_body,
               created_at, expires_at)
            values (:key, 'old-hash', 201, '{}', '{}', now() - interval '2 days',
                    now() - interval '1 day')
            """)
        .param("key", clave)
        .update();
    var nombre = nombreUnico();

    var resultado = registrar(clave, "{\"name\":\"" + nombre + "\",\"initialQuantity\":2}");

    assertThat(resultado).hasStatus(HttpStatus.CREATED);
    assertThat(contarProductosConNombre(nombre)).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-22 · 011-AC-01/02 repetir el nombre, aun en otra forma, responde 409")
  void debe_responder_conflicto_cuando_el_nombre_ya_existe_en_otra_forma() {
    var nombre = nombreUnico();
    registrar(claveNueva(), "{\"name\":\"" + nombre + "\",\"initialQuantity\":5}");
    var existente = idProductoDe(nombre);

    var repetido =
        registrar(
            claveNueva(), "{\"name\":\"  " + nombre.toUpperCase() + "  \",\"initialQuantity\":3}");

    assertThat(repetido).hasStatus(HttpStatus.CONFLICT);
    assertThat(repetido).bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_ALREADY_EXISTS");
    assertThat(repetido)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains(existente.toString());
    assertThat(contarProductosConNombre(nombre)).isEqualTo(1);
  }

  @Test
  @DisplayName("REQ-22 · 011-AC-03 el nombre se guarda y se devuelve normalizado")
  void debe_devolver_el_nombre_normalizado_cuando_trae_espacios_de_mas() {
    var base = nombreUnico();

    var resultado =
        registrar(claveNueva(), "{\"name\":\"  " + base + "   gamer \",\"initialQuantity\":1}");

    assertThat(resultado).hasStatus(HttpStatus.CREATED);
    assertThat(resultado).bodyJson().extractingPath("$.name").isEqualTo(base + " gamer");
  }

  @Test
  @DisplayName("REQ-22 · 011-AC-04 un nombre con caracteres de control es 400")
  void debe_rechazar_el_nombre_cuando_trae_caracteres_de_control() {
    var resultado = registrar(claveNueva(), "{\"name\":\"Tecla\\u0007do\",\"initialQuantity\":1}");

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("REQ-24 · 011-AC-06 la misma clave con el mismo cuerpo reproduce el 201, no un 409")
  void debe_reproducir_el_201_cuando_se_repite_la_misma_clave_con_el_mismo_cuerpo() {
    var clave = claveNueva();
    var cuerpo = "{\"name\":\"" + nombreUnico() + "\",\"initialQuantity\":2}";

    var primero = registrar(clave, cuerpo);
    var segundo = registrar(clave, cuerpo);

    assertThat(primero).hasStatus(HttpStatus.CREATED);
    assertThat(segundo).hasStatus(HttpStatus.CREATED);
    assertThat(segundo).headers().hasValue("Idempotent-Replayed", "true");
  }

  @Test
  @DisplayName("REQ-22 · 011-AC-07 una propiedad desconocida, como un productId propio, es 400")
  void debe_rechazar_la_solicitud_cuando_trae_una_propiedad_desconocida() {
    var propio = UUID.randomUUID();

    var resultado =
        registrar(
            claveNueva(),
            "{\"name\":\""
                + nombreUnico()
                + "\",\"initialQuantity\":1,\"productId\":\""
                + propio
                + "\"}");

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("Propiedad desconocida «productId»")
        .contains("name")
        .contains("initialQuantity");
  }

  @Test
  @DisplayName("REQ-19 · 011 los errores de validación nombran los campos del contrato en inglés")
  void debe_nombrar_los_campos_del_contrato_cuando_el_producto_es_invalido() {
    var resultado = registrar(claveNueva(), "{\"name\":\"\",\"initialQuantity\":-1}");

    assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(resultado)
        .bodyJson()
        .extractingPath("$.detail")
        .asString()
        .contains("name ")
        .contains("initialQuantity ")
        .doesNotContain("nombre")
        .doesNotContain("cantidadInicial");
  }

  private MvcTestResult registrar(String claveIdempotencia, String cuerpo) {
    return mvc.post()
        .uri("/products")
        .header("Idempotency-Key", claveIdempotencia)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .exchange();
  }

  private UUID idProductoDe(String nombre) {
    return jdbc.sql("select id from products where name = :name")
        .param("name", nombre)
        .query(UUID.class)
        .single();
  }

  private int contarProductosConNombre(String nombre) {
    return jdbc.sql("select count(*) from products where name = :name")
        .param("name", nombre)
        .query(Integer.class)
        .single();
  }

  private static String claveNueva() {
    return UUID.randomUUID().toString();
  }

  @Test
  @DisplayName("REQ-28 · los endpoints están documentados con OpenAPI")
  void debe_publicar_el_documento_openapi_con_todos_los_endpoints() {
    var documento = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(documento).hasStatus(HttpStatus.OK);
    assertThat(documento)
        .bodyJson()
        .extractingPath("$.paths['/products/{productId}/stock']")
        .isNotNull();
  }

  private static String nombreUnico() {
    return "product-" + UUID.randomUUID();
  }
}
