package com.roshka.challenge.pedidos;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Hace de la otra parte del tópico: envía registros crudos y recoge lo que publica el servicio. */
public final class SondaKafka implements AutoCloseable {

  private final KafkaProducer<String, String> productor;
  private final KafkaConsumer<String, String> consumidor;
  private final List<ConsumerRecord<String, String>> recibido = new ArrayList<>();

  public SondaKafka(String servidoresBootstrap, String topicoEscucha) {
    productor =
        new KafkaProducer<>(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                servidoresBootstrap,
                ProducerConfig.ACKS_CONFIG,
                "all"),
            new StringSerializer(),
            new StringSerializer());
    consumidor =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                servidoresBootstrap,
                ConsumerConfig.GROUP_ID_CONFIG,
                "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"),
            new StringDeserializer(),
            new StringDeserializer());
    consumidor.subscribe(List.of(topicoEscucha));
  }

  public void enviar(String topico, String clave, String valor) {
    try {
      productor.send(new ProducerRecord<>(topico, clave, valor)).get();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo enviar el registro de prueba", e);
    }
  }

  /**
   * Sondea una vez y devuelve todos los registros recibidos hasta ahora cuya key es la indicada.
   */
  public List<ConsumerRecord<String, String>> recibidoPara(String clave) {
    consumidor.poll(Duration.ofMillis(200)).forEach(recibido::add);
    return recibido.stream().filter(registro -> clave.equals(registro.key())).toList();
  }

  @Override
  public void close() {
    productor.close();
    consumidor.close();
  }
}
