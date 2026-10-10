package com.roshka.challenge.pedidos.adaptadores.entrada.mensajeria;

import com.roshka.challenge.contrato.StockLiberado;
import com.roshka.challenge.contrato.StockRechazado;
import com.roshka.challenge.contrato.StockReservado;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ManejarRespuestaInventario;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ResultadoManejo;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
class RespuestaInventarioListener {

  private final LectorMensajes lector;
  private final ManejarRespuestaInventario respuestas;
  private final MeterRegistry medidores;

  RespuestaInventarioListener(
      LectorMensajes lector, ManejarRespuestaInventario respuestas, MeterRegistry medidores) {
    this.lector = lector;
    this.respuestas = respuestas;
    this.medidores = medidores;
  }

  /** Se confirma solo después de que commitea la transacción del paso de la saga (ADR 0002). */
  @KafkaListener(topics = Topicos.INVENTARIO_EVENTOS)
  void alResponder(ConsumerRecord<String, String> registro, Acknowledgment ack) {
    var valor = registro.value();
    var vistaPrevia = lector.vistaPrevia(valor);
    var resultado =
        RegistroEventos.procesar(
            registro,
            vistaPrevia,
            () ->
                switch (vistaPrevia.tipo()) {
                  case TiposMensaje.STOCK_RESERVADO ->
                      respuestas.alStockReservado(lector.leer(valor, StockReservado.class));
                  case TiposMensaje.STOCK_RECHAZADO ->
                      respuestas.alStockRechazado(lector.leer(valor, StockRechazado.class));
                  case TiposMensaje.STOCK_LIBERADO ->
                      respuestas.alStockLiberado(lector.leer(valor, StockLiberado.class));
                  default ->
                      throw new MensajeMalformado("Tipo inesperado en " + registro.topic(), null);
                });
    if (resultado == ResultadoManejo.DUPLICADO) {
      medidores.counter("messages.duplicated", "type", vistaPrevia.tipo()).increment();
    }
    ack.acknowledge();
  }
}
