package com.roshka.challenge.pedidos.adaptadores.salida.mensajeria;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.javacrumbs.shedlock.core.LockExtender;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica las filas del outbox en orden de id, una instancia a la vez (ShedLock), con key =
 * idAgregado. Una fila se marca publicada solo cuando el broker la confirmó; si falla, el lote se
 * detiene para que un mensaje posterior del mismo pedido nunca adelante a uno anterior (ADR 0003).
 * Cada envío continúa la traza guardada con la fila, no la del job de sondeo.
 */
@Component
class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final TypeReference<Map<String, String>> CABECERAS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final OutboxProperties properties;
  private final Clock reloj;
  private final JsonMapper json;
  private final Tracer tracer;
  private final Propagator propagador;
  private final Timer retrasoPublicacion;

  OutboxPublisher(
      JdbcClient jdbc,
      KafkaTemplate<String, String> kafka,
      OutboxProperties properties,
      Clock reloj,
      JsonMapper json,
      Tracer tracer,
      Propagator propagador,
      MeterRegistry medidores) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.properties = properties;
    this.reloj = reloj;
    this.json = json;
    this.tracer = tracer;
    this.propagador = propagador;
    this.retrasoPublicacion =
        Timer.builder("outbox.publish.lag")
            .description(
                "Tiempo entre escribir un mensaje en el outbox y la confirmación del broker")
            .register(medidores);
    Gauge.builder("outbox.pending.messages", this::mensajesPendientes)
        .description("Filas del outbox todavía sin publicar")
        .register(medidores);
  }

  private record MensajePendiente(
      long id,
      String topico,
      UUID idAgregado,
      String carga,
      String cabeceras,
      OffsetDateTime creadoEn) {}

  @Scheduled(
      fixedDelayString = "${outbox.publicador.intervalo-sondeo}",
      initialDelayString = "${outbox.publicador.intervalo-sondeo}")
  @SchedulerLock(name = "outbox-publisher")
  public void publicarPendientes() {
    var pendiente =
        jdbc.sql(
                """
                select id, topic, aggregate_id, payload::text as payload,
                       headers::text as headers, created_at
                  from outbox where published_at is null order by id limit :limit
                """)
            .param("limit", properties.tamanioLote())
            .query(
                (rs, n) ->
                    new MensajePendiente(
                        rs.getLong("id"),
                        rs.getString("topic"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("payload"),
                        rs.getString("headers"),
                        rs.getObject("created_at", OffsetDateTime.class)))
            .list();
    for (var mensaje : pendiente) {
      LockExtender.extendActiveLock(properties.bloqueoMaximo(), Duration.ZERO);
      if (!enviar(mensaje)) {
        return;
      }
      var ahora = OffsetDateTime.now(reloj);
      jdbc.sql("update outbox set published_at = :now where id = :id")
          .param("now", ahora)
          .param("id", mensaje.id())
          .update();
      retrasoPublicacion.record(Duration.between(mensaje.creadoEn(), ahora));
    }
  }

  private void registrarPublicacion(MensajePendiente mensaje, long ms) {
    var sobre = json.readTree(mensaje.carga());
    var tipo = sobre.path("tipo").asString("");
    log.atInfo()
        .addKeyValue("topic", mensaje.topico())
        .addKeyValue("orderId", mensaje.idAgregado().toString())
        .addKeyValue("messageId", sobre.path("idMensaje").asString(""))
        .addKeyValue("type", tipo)
        .addKeyValue("correlationId", sobre.path("idCorrelacion").asString(""))
        .addKeyValue("durationMs", ms)
        .log(
            "Evento publicado: %s en %s (%d ms) del pedido %s"
                .formatted(tipo, mensaje.topico(), ms, mensaje.idAgregado()));
  }

  private boolean enviar(MensajePendiente mensaje) {
    var contexto = json.readValue(mensaje.cabeceras(), CABECERAS);
    var span =
        propagador.extract(contexto, Map::get).name("outbox publish " + mensaje.topico()).start();
    try (var alcance = tracer.withSpan(span)) {
      var desde = System.nanoTime();
      kafka
          .send(
              new ProducerRecord<>(
                  mensaje.topico(), mensaje.idAgregado().toString(), mensaje.carga()))
          .get(properties.timeoutEnvio().toMillis(), TimeUnit.MILLISECONDS);
      var ms = Duration.ofNanos(System.nanoTime() - desde).toMillis();
      try {
        registrarPublicacion(mensaje, ms);
      } catch (RuntimeException e) {
        log.atWarn()
            .setCause(e)
            .log(
                "Evento publicado, pero no se pudo escribir su log (fila {} del outbox)",
                mensaje.id());
      }
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ExecutionException | TimeoutException e) {
      span.error(e);
      log.atWarn()
          .addKeyValue("outboxId", mensaje.id())
          .addKeyValue("topic", mensaje.topico())
          .addKeyValue("orderId", mensaje.idAgregado().toString())
          .setCause(e)
          .log(
              "Outbox: fila %s sin publicar en %s (%s); se reintenta en el próximo ciclo"
                  .formatted(mensaje.id(), mensaje.topico(), motivo(e)));
      return false;
    } finally {
      span.end();
    }
  }

  private long mensajesPendientes() {
    return jdbc.sql("select count(*) from outbox where published_at is null")
        .query(Long.class)
        .single();
  }

  private static String motivo(Exception e) {
    var causa = e.getCause() != null ? e.getCause() : e;
    return causa.getMessage() != null ? causa.getMessage() : causa.getClass().getSimpleName();
  }
}
