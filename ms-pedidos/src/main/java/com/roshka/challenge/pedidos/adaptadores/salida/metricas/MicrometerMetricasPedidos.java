package com.roshka.challenge.pedidos.adaptadores.salida.metricas;

import com.roshka.challenge.pedidos.aplicacion.puertos.salida.MetricasPedidos;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registra después del commit, para que una transacción que se revierte (un conflicto optimista que
 * se reintentará) nunca se cuente.
 */
@Component
class MicrometerMetricasPedidos implements MetricasPedidos {

  private final MeterRegistry medidores;

  MicrometerMetricasPedidos(MeterRegistry medidores) {
    this.medidores = medidores;
  }

  @Override
  public void pedidoColocado() {
    trasCommit(() -> medidores.counter("orders.placed").increment());
  }

  @Override
  public void pedidoTerminado(
      EstadoPedido estado, MotivoRechazoPedido motivo, Duration duracionSaga) {
    var etiquetaMotivo = motivo == null ? "NONE" : motivo.name();
    trasCommit(
        () -> {
          medidores
              .counter("orders.by.final.status", "status", estado.name(), "reason", etiquetaMotivo)
              .increment();
          Timer.builder("saga.duration")
              .description("Desde que se coloca el pedido hasta su primer estado terminal")
              .tag("status", estado.name())
              .register(medidores)
              .record(duracionSaga);
        });
  }

  private static void trasCommit(Runnable accion) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      accion.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            accion.run();
          }
        });
  }
}
