package com.roshka.challenge.inventario.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import com.roshka.challenge.inventario.NombresUnicos;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Con Redis inalcanzable, las escrituras no se ejecutan y responden 503. */
@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
@Import(BloqueoNoDisponibleIT.RedisCaido.class)
class BloqueoNoDisponibleIT {

  @TestConfiguration
  static class RedisCaido {

    @Bean
    @Primary
    RedissonClient redissonInalcanzable() {
      var config = new Config();
      config.setLazyInitialization(true);
      config
          .useSingleServer()
          .setAddress("redis://localhost:1")
          .setConnectTimeout(500)
          .setTimeout(500)
          .setRetryAttempts(0);
      return Redisson.create(config);
    }
  }

  @Autowired MockMvcTester mvc;

  @Test
  @DisplayName(
      "REQ-02 REQ-24 · 015-AC-07 sin Redis una escritura es 503 LOCK_UNAVAILABLE y no se ejecuta")
  void debe_responder_servicio_no_disponible_cuando_no_se_puede_obtener_el_bloqueo() {
    var registro =
        mvc.post()
            .uri("/products")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + NombresUnicos.de("sin-redis") + "\",\"initialQuantity\":1}")
            .exchange();
    var ajuste =
        mvc.put()
            .uri("/products/{id}/stock", UUID.randomUUID())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"available\":3}")
            .exchange();

    assertThat(registro).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(registro).bodyJson().extractingPath("$.code").isEqualTo("LOCK_UNAVAILABLE");
    assertThat(ajuste).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(ajuste).bodyJson().extractingPath("$.code").isEqualTo("LOCK_UNAVAILABLE");
  }
}
