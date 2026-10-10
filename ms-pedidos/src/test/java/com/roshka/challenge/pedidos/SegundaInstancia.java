package com.roshka.challenge.pedidos;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Otra instancia de ms-pedidos en la misma JVM, en su propio puerto, compartiendo los contenedores.
 */
public final class SegundaInstancia implements AutoCloseable {

  private final ConfigurableApplicationContext contexto;

  private SegundaInstancia(ConfigurableApplicationContext contexto) {
    this.contexto = contexto;
  }

  public static SegundaInstancia iniciar(Class<?>... configuracionExtra) {
    var fuentes = new ArrayList<Class<?>>(List.of(PedidosApplication.class));
    fuentes.addAll(List.of(configuracionExtra));
    var contexto =
        new SpringApplicationBuilder(fuentes.toArray(Class<?>[]::new))
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + ContenedoresCompartidos.POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + ContenedoresCompartidos.POSTGRES.getUsername(),
                "--spring.datasource.password=" + ContenedoresCompartidos.POSTGRES.getPassword(),
                "--spring.kafka.bootstrap-servers="
                    + ContenedoresCompartidos.KAFKA.getBootstrapServers(),
                "--bloqueo.redis.direccion=redis://"
                    + ContenedoresCompartidos.REDIS.getHost()
                    + ":"
                    + ContenedoresCompartidos.REDIS.getMappedPort(6379));
    return new SegundaInstancia(contexto);
  }

  public String urlBase() {
    return "http://localhost:" + contexto.getEnvironment().getProperty("local.server.port");
  }

  public <T> T bean(Class<T> tipo) {
    return contexto.getBean(tipo);
  }

  @Override
  public void close() {
    contexto.close();
  }
}
