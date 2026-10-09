package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"paginacion.tamanio-maximo=500", "paginacion.tamanio-por-defecto=50"})
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class OpenApiConfigurableIT {

  @Autowired MockMvcTester mvc;
  @Autowired JsonMapper json;

  @Test
  @DisplayName("EXT-04 · 015-AC-04 el Swagger sigue los límites de paginación configurados")
  void debe_documentar_los_limites_configurados_cuando_cambia_la_paginacion() throws Exception {
    var documento =
        json.readTree(
            mvc.get()
                .uri("/v3/api-docs")
                .exchange()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    for (var ruta : new String[] {"/products", "/products/{productId}/stock/movements"}) {
      for (var parametro : documento.path("paths").path(ruta).path("get").path("parameters")) {
        if ("size".equals(parametro.path("name").asString())) {
          assertThat(parametro.path("schema").path("maximum").asInt()).isEqualTo(500);
          assertThat(parametro.path("schema").path("default").asInt()).isEqualTo(50);
          assertThat(parametro.path("description").asString()).contains("500");
        }
      }
    }
  }
}
