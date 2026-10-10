package com.roshka.challenge.pedidos.adaptadores.entrada.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.pedidos.ContenedoresCompartidos;
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
    var colocar =
        mvc.post()
            .uri("/orders")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"lines\":[{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}")
            .exchange();
    var cancelar =
        mvc.delete()
            .uri("/orders/{id}", UUID.randomUUID())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .exchange();

    assertThat(colocar).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(colocar).bodyJson().extractingPath("$.code").isEqualTo("LOCK_UNAVAILABLE");
    assertThat(cancelar).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(cancelar).bodyJson().extractingPath("$.code").isEqualTo("LOCK_UNAVAILABLE");
  }
}
