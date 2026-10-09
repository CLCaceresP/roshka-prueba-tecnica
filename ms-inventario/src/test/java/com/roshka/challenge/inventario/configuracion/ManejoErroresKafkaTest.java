package com.roshka.challenge.inventario.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.adaptadores.entrada.mensajeria.MensajeMalformado;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

class ManejoErroresKafkaTest {

  @Test
  @DisplayName(
      "REQ-18 · 003-AC-15 las métricas de DLT cuentan confirmaciones del broker, no intentos fallidos")
  @SuppressWarnings("unchecked")
  void debe_contar_un_mensaje_muerto_cuando_la_publicacion_fallida_se_reintenta_con_exito() {
    var topico = Topicos.INVENTARIO_COMANDOS;
    var destino = topico + Topicos.SUFIJO_MENSAJE_MUERTO;
    var plantilla = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
    when(plantilla.partitionsFor(destino))
        .thenReturn(List.of(new PartitionInfo(destino, 0, null, null, null)));
    var enviado = new ProducerRecord<String, String>(destino, 0, "order-id", "invalid");
    var metadatos = new RecordMetadata(new TopicPartition(destino, 0), 0, 0, 0, 0, 0);
    when(plantilla.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("broker no disponible")),
            CompletableFuture.completedFuture(new SendResult<>(enviado, metadatos)));
    var medidores = new SimpleMeterRegistry();
    var reintentar =
        new ReintentosProperties(Duration.ofMillis(1), 2, Duration.ofMillis(1), Duration.ZERO);
    var manejador =
        new ManejoErroresKafka().manejadorErroresKafka(plantilla, reintentar, medidores);
    var registro = new ConsumerRecord<String, String>(topico, 0, 0, "order-id", "invalid");
    var fallo = new MensajeMalformado("invalid record", null);
    var consumidor = mock(Consumer.class);
    var contenedor = mock(MessageListenerContainer.class);

    assertThat(manejador.handleOne(fallo, registro, consumidor, contenedor)).isFalse();
    assertThat(medidores.counter("messages.dlt", "topic", topico).count()).isZero();

    assertThat(manejador.handleOne(fallo, registro, consumidor, contenedor)).isTrue();
    assertThat(medidores.counter("messages.dlt", "topic", topico).count()).isEqualTo(1);
  }
}
