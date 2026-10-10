package com.roshka.challenge.inventario.adaptadores.salida.metricas;

import com.roshka.challenge.inventario.aplicacion.puertos.salida.MetricasInventario;
import com.roshka.challenge.inventario.dominio.MotivoRechazo;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registra después del commit, para que un intento revertido que se reintentará nunca se cuente.
 */
@Component
class MicrometerMetricasInventario implements MetricasInventario {

  private final MeterRegistry medidores;

  MicrometerMetricasInventario(MeterRegistry medidores) {
    this.medidores = medidores;
  }

  @Override
  public void reservaDecidida(MotivoRechazo rechazo) {
    var resultado = rechazo == null ? "reserved" : "rejected";
    var motivo = rechazo == null ? "NONE" : rechazo.name();
    Runnable registro =
        () ->
            medidores
                .counter("stock.reservations", "result", resultado, "reason", motivo)
                .increment();
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      registro.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            registro.run();
          }
        });
  }
}
