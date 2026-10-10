package com.roshka.challenge.inventario.configuracion;

import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.adaptadores.entrada.mensajeria.MensajeMalformado;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Los reintentos bloqueantes con backoff exponencial conservan el orden por partición; lo que
 * reintentar no arregla va directo a {@code <topico>.DLT}, en la misma partición, con la causa en
 * los headers (ADR 0006).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReintentosProperties.class)
class ManejoErroresKafka {

  private static final Logger log = LoggerFactory.getLogger(ManejoErroresKafka.class);

  @Bean
  DefaultErrorHandler manejadorErroresKafka(
      KafkaTemplate<String, String> plantilla,
      ReintentosProperties reintentar,
      MeterRegistry medidores) {
    // Se registran por adelantado para que los dashboards muestren cero en lugar de «sin datos».
    medidores.counter("messages.dlt", "topic", Topicos.INVENTARIO_COMANDOS);
    var mensajesMuertos =
        new DeadLetterPublishingRecoverer(
            plantilla,
            (registro, fallo) ->
                new TopicPartition(
                    registro.topic() + Topicos.SUFIJO_MENSAJE_MUERTO, registro.partition()));
    mensajesMuertos.setFailIfSendResultIsError(true);
    var retroceso =
        new ExponentialBackOff(
            reintentar.intervaloInicial().toMillis(), reintentar.multiplicador());
    retroceso.setMaxInterval(reintentar.intervaloMaximo().toMillis());
    retroceso.setMaxElapsedTime(reintentar.tiempoMaximoTranscurrido().toMillis());
    var manejador =
        new DefaultErrorHandler(
            (registro, fallo) -> {
              mensajesMuertos.accept(registro, fallo);
              medidores.counter("messages.dlt", "topic", registro.topic()).increment();
              log.atError()
                  .setCause(fallo)
                  .addKeyValue("topic", registro.topic())
                  .addKeyValue("partition", registro.partition())
                  .addKeyValue("offset", registro.offset())
                  .addKeyValue("key", registro.key())
                  .log("Evento enviado a la DLT");
            },
            retroceso);
    manejador.addNotRetryableExceptions(MensajeMalformado.class);
    return manejador;
  }
}
