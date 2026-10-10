package com.roshka.challenge.pedidos;

import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

/** Los mismos escenarios con el bloqueo distribuido desactivado en ambas instancias (ADR 0005). */
@Tag("concurrency")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
@Import(SinBloqueos.class)
@ImportTestcontainers(ContenedoresCompartidos.class)
class PedidoConcurrenciaSinBloqueosIT extends EscenariosConcurrenciaPedido {

  @Override
  SegundaInstancia iniciarSegundaInstancia() {
    return SegundaInstancia.iniciar(SinBloqueos.class);
  }

  @Override
  Set<Integer> estadosDuplicadosConcurrentes() {
    return Set.of(202);
  }
}
