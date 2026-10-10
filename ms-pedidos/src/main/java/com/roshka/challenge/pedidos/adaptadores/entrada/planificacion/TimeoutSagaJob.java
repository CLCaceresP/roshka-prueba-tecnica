package com.roshka.challenge.pedidos.adaptadores.entrada.planificacion;

import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ExpirarSagasEstancadas;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Una instancia a la vez busca sagas estancadas (ADR 0013). */
@Component
class TimeoutSagaJob {

  private final ExpirarSagasEstancadas expirarSagasEstancadas;

  TimeoutSagaJob(ExpirarSagasEstancadas expirarSagasEstancadas) {
    this.expirarSagasEstancadas = expirarSagasEstancadas;
  }

  @Scheduled(fixedDelayString = "${saga.timeout.intervalo-verificacion}")
  @SchedulerLock(name = "saga-timeout")
  void expirarEstancadas() {
    expirarSagasEstancadas.expirarEstancadas();
  }
}
