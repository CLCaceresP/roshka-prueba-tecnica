package com.roshka.challenge.pedidos;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Contenedores compartidos por todos los contextos de Spring de la JVM de tests; se levantan una
 * vez y los detiene Ryuk.
 */
public final class ContenedoresCompartidos {

  @Container @ServiceConnection
  public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Container @ServiceConnection
  public static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.2.1");

  @Container
  public static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

  private ContenedoresCompartidos() {}

  @DynamicPropertySource
  static void propiedadesRedis(DynamicPropertyRegistry registro) {
    registro.add(
        "bloqueo.redis.direccion",
        () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
  }
}
