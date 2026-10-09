package com.roshka.challenge.inventario.adaptadores.salida.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.ContenedoresCompartidos;
import com.roshka.challenge.inventario.SondaKafka;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockExtender.LockCanNotBeExtendedException;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "outbox.publicador.intervalo-sondeo=1h")
@DirtiesContext
@ImportTestcontainers(ContenedoresCompartidos.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class OutboxPublisherIT {

  private static final Duration LEASE = Duration.ofSeconds(2);
  private static final int FILAS = 3;
  private static final String TOPICO = Topicos.INVENTARIO_EVENTOS;
  @Autowired DataSource dataSource;
  @Autowired JdbcClient jdbc;
  @Autowired JsonMapper json;
  @Autowired Tracer tracer;
  @Autowired Propagator propagador;

  @MockitoSpyBean(name = "kafkaTemplate")
  KafkaTemplate kafka;

  @BeforeEach
  void limpiarFilasPendientes() {
    jdbc.sql("delete from outbox").update();
  }

  @Test
  @DisplayName(
      "REQ-06 REQ-26 · 003-AC-14 otro publisher no puede tomar el control durante un lote lento")
  @SuppressWarnings("unchecked")
  void debe_dejar_fuera_al_competidor_cuando_el_lote_excede_el_lease_inicial() {
    var idPedido = insertarFilas();
    var nombre = "publisher-" + UUID.randomUUID();
    var duenio = proveedor();
    var competidor = proveedor();
    var adquirido = new AtomicBoolean();
    var intentos = new AtomicInteger();
    var iniciado = Instant.now();
    try (var retrasos = Executors.newSingleThreadScheduledExecutor();
        var broker = new SondaKafka(ContenedoresCompartidos.KAFKA.getBootstrapServers(), TOPICO)) {
      doAnswer(
              invocacion -> {
                var original =
                    (CompletableFuture<SendResult<String, String>>) invocacion.callRealMethod();
                var retrasado = new CompletableFuture<SendResult<String, String>>();
                original.whenComplete(
                    (resultado, fallo) ->
                        retrasos.schedule(
                            () -> {
                              if (Duration.between(iniciado, Instant.now()).compareTo(LEASE) > 0) {
                                intentos.incrementAndGet();
                                competidor
                                    .lock(configuracion(nombre))
                                    .ifPresent(
                                        bloqueo -> {
                                          adquirido.set(true);
                                          bloqueo.unlock();
                                        });
                              }
                              if (fallo == null) {
                                retrasado.complete(resultado);
                              } else {
                                retrasado.completeExceptionally(fallo);
                              }
                            },
                            800,
                            TimeUnit.MILLISECONDS));
                return retrasado;
              })
          .when(kafka)
          .send(any(ProducerRecord.class));

      new DefaultLockingTaskExecutor(duenio)
          .executeWithLock((Runnable) publicador()::publicarPendientes, configuracion(nombre));
      var recibido =
          await()
              .atMost(Duration.ofSeconds(10))
              .until(
                  () -> broker.recibidoPara(idPedido.toString()),
                  registros -> registros.size() >= FILAS);
      assertThat(recibido)
          .extracting(registro -> json.readTree(registro.value()).get("sequence").asInt())
          .containsExactly(0, 1, 2);
    }

    assertThat(intentos.get()).isPositive();
    assertThat(adquirido.get()).isFalse();
    assertThat(filasPublicadas()).isEqualTo(FILAS);
  }

  @Test
  @DisplayName("REQ-06 REQ-26 · 003-AC-14 perder el lease detiene el lote antes de otro envío")
  @SuppressWarnings("unchecked")
  void debe_dejar_pendientes_las_filas_restantes_cuando_el_bloqueo_no_se_puede_renovar() {
    insertarFilas();
    var nombre = "publisher-" + UUID.randomUUID();
    var envios = new AtomicInteger();
    doAnswer(
            invocacion -> {
              var original =
                  (CompletableFuture<SendResult<String, String>>) invocacion.callRealMethod();
              envios.incrementAndGet();
              return original.thenApply(
                  resultado -> {
                    jdbc.sql(
                            "update shedlock set lock_until = timezone('UTC', now()) - interval '1 second' where name = :name")
                        .param("name", nombre)
                        .update();
                    return resultado;
                  });
            })
        .when(kafka)
        .send(any(ProducerRecord.class));

    assertThatThrownBy(
            () ->
                new DefaultLockingTaskExecutor(proveedor())
                    .executeWithLock(
                        (Runnable) publicador()::publicarPendientes, configuracion(nombre)))
        .isInstanceOfAny(LockCanNotBeExtendedException.class, IllegalStateException.class);
    assertThat(envios.get()).isEqualTo(1);
    assertThat(filasPublicadas()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "REQ-06 REQ-26 · 003-AC-09 los envíos fallidos quedan pendientes y se publican al recuperarse")
  void debe_reintentar_las_filas_pendientes_cuando_el_broker_se_recupera() {
    insertarFilas();
    var envios = new AtomicInteger();
    doAnswer(
            invocacion ->
                envios.getAndIncrement() == 0
                    ? CompletableFuture.failedFuture(
                        new IllegalStateException("broker no disponible"))
                    : invocacion.callRealMethod())
        .when(kafka)
        .send(any(ProducerRecord.class));
    var ejecutor = new DefaultLockingTaskExecutor(proveedor());
    var nombre = "publisher-" + UUID.randomUUID();

    ejecutor.executeWithLock((Runnable) publicador()::publicarPendientes, configuracion(nombre));
    assertThat(filasPublicadas()).isZero();

    ejecutor.executeWithLock((Runnable) publicador()::publicarPendientes, configuracion(nombre));
    assertThat(envios.get()).isEqualTo(FILAS + 1);
    assertThat(filasPublicadas()).isEqualTo(FILAS);
  }

  private OutboxPublisher publicador() {
    return new OutboxPublisher(
        jdbc,
        kafka,
        new OutboxProperties(Duration.ofHours(1), FILAS, LEASE, LEASE),
        Clock.systemUTC(),
        json,
        tracer,
        propagador,
        new SimpleMeterRegistry());
  }

  private LockProvider proveedor() {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }

  private LockConfiguration configuracion(String nombre) {
    return new LockConfiguration(Instant.now(), nombre, LEASE, Duration.ZERO);
  }

  private UUID insertarFilas() {
    var idPedido = UUID.randomUUID();
    for (int i = 0; i < FILAS; i++) {
      jdbc.sql(
              """
              insert into outbox (message_id, aggregate_id, topic, type, payload, created_at)
              values (:messageId, :orderId, :topic, 'Test', cast(:payload as jsonb), now())
              """)
          .param("messageId", UUID.randomUUID())
          .param("orderId", idPedido)
          .param("topic", TOPICO)
          .param("payload", "{\"sequence\":" + i + "}")
          .update();
    }
    return idPedido;
  }

  private long filasPublicadas() {
    return jdbc.sql("select count(*) from outbox where published_at is not null")
        .query(Long.class)
        .single();
  }
}
