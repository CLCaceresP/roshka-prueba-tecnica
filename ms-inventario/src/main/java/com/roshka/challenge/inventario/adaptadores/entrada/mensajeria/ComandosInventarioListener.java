package com.roshka.challenge.inventario.adaptadores.entrada.mensajeria;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ManejarLiberarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ManejarReservarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ResultadoManejo;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
class ComandosInventarioListener {

  private final LectorMensajes lector;
  private final ManejarReservarStock reservarStock;
  private final ManejarLiberarStock liberarStock;
  private final MeterRegistry medidores;

  ComandosInventarioListener(
      LectorMensajes lector,
      ManejarReservarStock reservarStock,
      ManejarLiberarStock liberarStock,
      MeterRegistry medidores) {
    this.lector = lector;
    this.reservarStock = reservarStock;
    this.liberarStock = liberarStock;
    this.medidores = medidores;
  }

  /** Se confirma solo después de que commitea la transacción del caso de uso (ADR 0002). */
  @KafkaListener(topics = Topicos.INVENTARIO_COMANDOS)
  void alComando(ConsumerRecord<String, String> registro, Acknowledgment ack) {
    var valor = registro.value();
    var vistaPrevia = lector.vistaPrevia(valor);
    var resultado =
        RegistroEventos.procesar(
            registro,
            vistaPrevia,
            () ->
                switch (vistaPrevia.tipo()) {
                  case TiposMensaje.RESERVAR_STOCK ->
                      reservarStock.manejar(lector.leer(valor, ReservarStock.class));
                  case TiposMensaje.LIBERAR_STOCK ->
                      liberarStock.manejar(lector.leer(valor, LiberarStock.class));
                  default ->
                      throw new MensajeMalformado("Tipo inesperado en " + registro.topic(), null);
                });
    if (resultado == ResultadoManejo.DUPLICADO) {
      medidores.counter("messages.duplicated", "type", vistaPrevia.tipo()).increment();
    }
    ack.acknowledge();
  }
}
