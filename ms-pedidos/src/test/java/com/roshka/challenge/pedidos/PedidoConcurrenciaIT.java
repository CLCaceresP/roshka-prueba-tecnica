package com.roshka.challenge.pedidos;

import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.annotation.DirtiesContext;

@Tag("concurrency")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ImportTestcontainers(ContenedoresCompartidos.class)
class PedidoConcurrenciaIT extends EscenariosConcurrenciaPedido {

  @Override
  SegundaInstancia iniciarSegundaInstancia() {
    return SegundaInstancia.iniciar();
  }

  @Override
  Set<Integer> estadosDuplicadosConcurrentes() {
    return Set.of(202, 409);
  }
}
