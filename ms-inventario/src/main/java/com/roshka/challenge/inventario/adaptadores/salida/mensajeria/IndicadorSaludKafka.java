package com.roshka.challenge.inventario.adaptadores.salida.mensajeria;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/** Forma parte del grupo readiness: Spring Boot no trae un health indicator de Kafka. */
@Component("kafka")
class IndicadorSaludKafka extends AbstractHealthIndicator {

  private final KafkaAdmin admin;
  private final Duration timeout;

  IndicadorSaludKafka(KafkaAdmin admin, @Value("${mensajeria.timeout-salud}") Duration timeout) {
    this.admin = admin;
    this.timeout = timeout;
  }

  @Override
  protected void doHealthCheck(Health.Builder constructor) throws Exception {
    try (var cliente = AdminClient.create(admin.getConfigurationProperties())) {
      var cluster =
          cliente.describeCluster(new DescribeClusterOptions().timeoutMs((int) timeout.toMillis()));
      var nodos = cluster.nodes().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      constructor.up().withDetail("nodes", nodos.size());
    }
  }
}
