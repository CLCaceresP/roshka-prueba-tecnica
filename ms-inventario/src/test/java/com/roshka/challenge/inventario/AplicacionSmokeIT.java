package com.roshka.challenge.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@ImportTestcontainers(ContenedoresCompartidos.class)
class AplicacionSmokeIT {

  @Autowired MockMvcTester mvc;

  @Test
  void debe_informar_saludable_cuando_arranca_contra_infraestructura_real() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatus(HttpStatus.OK);
  }
}
