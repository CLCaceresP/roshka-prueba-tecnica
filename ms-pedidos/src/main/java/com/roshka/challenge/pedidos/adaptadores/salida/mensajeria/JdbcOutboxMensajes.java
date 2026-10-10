package com.roshka.challenge.pedidos.adaptadores.salida.mensajeria;

import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.OutboxMensajes;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe en la transacción del llamador; {@link OutboxPublisher} envía la fila después del commit.
 * El contexto de traza actual se guarda con la fila para que la publicación continúe la traza de la
 * solicitud o el mensaje que la originó (spec 006, RN-03).
 */
@Repository
class JdbcOutboxMensajes implements OutboxMensajes {

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock reloj;
  private final Tracer tracer;
  private final Propagator propagador;

  JdbcOutboxMensajes(
      JdbcClient jdbc, JsonMapper json, Clock reloj, Tracer tracer, Propagator propagador) {
    this.jdbc = jdbc;
    this.json = json;
    this.reloj = reloj;
    this.tracer = tracer;
    this.propagador = propagador;
  }

  @Override
  public void agregar(String topico, SobreMensaje<?> sobre) {
    jdbc.sql(
            """
            insert into outbox
              (message_id, aggregate_id, topic, type, payload, headers, created_at)
            values (:messageId, :aggregateId, :topic, :type, cast(:payload as jsonb),
                    cast(:headers as jsonb), :createdAt)
            """)
        .param("messageId", sobre.idMensaje())
        .param("aggregateId", sobre.idAgregado())
        .param("topic", topico)
        .param("type", sobre.tipo())
        .param("payload", json.writeValueAsString(sobre))
        .param("headers", json.writeValueAsString(contextoTraza()))
        .param("createdAt", OffsetDateTime.now(reloj))
        .update();
  }

  private Map<String, String> contextoTraza() {
    var cabeceras = new HashMap<String, String>();
    var span = tracer.currentSpan();
    if (span != null) {
      propagador.inject(span.context(), cabeceras, Map::put);
    }
    return cabeceras;
  }
}
