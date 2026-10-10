package com.roshka.challenge.pedidos.adaptadores.entrada.mensajeria;

import com.roshka.challenge.pedidos.adaptadores.entrada.mensajeria.LectorMensajes.VistaPrevia;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ResultadoManejo;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Registra el ciclo de vida de un evento de Kafka (spec 010, RN-07): recibido, procesando y
 * finalizado o fallido. Mientras dura el procesamiento, los detalles del evento viven en el MDC,
 * así que todo log de los pasos los lleva también.
 */
final class RegistroEventos {

  private static final Logger log = LoggerFactory.getLogger(RegistroEventos.class);
  private static final List<String> CLAVES =
      List.of(
          "messageId",
          "orderId",
          "correlationId",
          "causationId",
          "eventType",
          "eventTopic",
          "eventPartition",
          "eventOffset");

  private RegistroEventos() {}

  static ResultadoManejo procesar(
      ConsumerRecord<String, String> registro,
      VistaPrevia evento,
      Supplier<ResultadoManejo> manejo) {
    abrirContexto(registro, evento);
    try {
      var inicio = System.nanoTime();
      log.atInfo().log(
          "Evento recibido: %s del pedido %s (%s partición %d, offset %d)%s"
              .formatted(
                  evento.tipo(),
                  evento.idPedido(),
                  registro.topic(),
                  registro.partition(),
                  registro.offset(),
                  evento.resumen().isEmpty() ? "" : ": " + evento.resumen()));
      log.atInfo().log(
          "Procesando evento: %s del pedido %s".formatted(evento.tipo(), evento.idPedido()));
      try {
        var resultado = manejo.get();
        var ms = milisegundos(inicio);
        log.atInfo()
            .addKeyValue("result", resultado)
            .addKeyValue("durationMs", ms)
            .log(
                "Evento finalizado: %s del pedido %s → %s en %d ms"
                    .formatted(evento.tipo(), evento.idPedido(), resultado, ms));
        return resultado;
      } catch (RuntimeException e) {
        var ms = milisegundos(inicio);
        log.atWarn()
            .addKeyValue("durationMs", ms)
            .addKeyValue("error", e.getClass().getSimpleName())
            .log(
                "Evento falló: %s del pedido %s tras %d ms: %s"
                    .formatted(
                        evento.tipo(),
                        evento.idPedido(),
                        ms,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        throw e;
      }
    } finally {
      CLAVES.forEach(MDC::remove);
    }
  }

  private static void abrirContexto(ConsumerRecord<String, String> registro, VistaPrevia evento) {
    MDC.put("messageId", evento.idMensaje());
    MDC.put("orderId", evento.idPedido());
    MDC.put("correlationId", evento.idCorrelacion());
    if (!evento.idCausa().isEmpty()) {
      MDC.put("causationId", evento.idCausa());
    }
    MDC.put("eventType", evento.tipo());
    MDC.put("eventTopic", registro.topic());
    MDC.put("eventPartition", String.valueOf(registro.partition()));
    MDC.put("eventOffset", String.valueOf(registro.offset()));
  }

  private static long milisegundos(long desdeNanos) {
    return Duration.ofNanos(System.nanoTime() - desdeNanos).toMillis();
  }
}
