package com.roshka.challenge.inventario.adaptadores.salida.bloqueo;

import com.roshka.challenge.inventario.aplicacion.puertos.salida.BloqueoDistribuido;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.BloqueoNoAdquirido;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.BloqueoNoDisponible;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class RedissonBloqueoDistribuido implements BloqueoDistribuido {

  private static final Logger log = LoggerFactory.getLogger(RedissonBloqueoDistribuido.class);

  private final RedissonClient redisson;
  private final MeterRegistry medidores;

  RedissonBloqueoDistribuido(RedissonClient redisson, MeterRegistry medidores) {
    this.redisson = redisson;
    this.medidores = medidores;
  }

  @Override
  public <T> T llamarConBloqueo(
      String nombre, Duration espera, Duration lease, Supplier<T> accion) {
    var bloqueo = redisson.getLock(nombre);
    var esperando = Timer.start(medidores);
    boolean adquirido;
    try {
      adquirido = bloqueo.tryLock(espera.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BloqueoNoAdquirido(nombre);
    } catch (RedisException e) {
      throw new BloqueoNoDisponible(nombre, e);
    }
    var esperaMs =
        Duration.ofNanos(
                esperando.stop(
                    medidores.timer(
                        "distributed.lock.wait",
                        "lock",
                        tipoDe(nombre),
                        "outcome",
                        adquirido ? "acquired" : "timeout")))
            .toMillis();
    if (!adquirido) {
      log.atWarn()
          .addKeyValue("lockName", nombre)
          .addKeyValue("waitMs", esperaMs)
          .log(
              "Bloqueo %s no adquirido tras esperar %d ms: otra operación lo retiene"
                  .formatted(nombre, esperaMs));
      throw new BloqueoNoAdquirido(nombre);
    }
    log.atInfo()
        .addKeyValue("lockName", nombre)
        .addKeyValue("waitMs", esperaMs)
        .log("Bloqueo %s adquirido en %d ms".formatted(nombre, esperaMs));
    var retenidoDesde = System.nanoTime();
    try {
      return accion.get();
    } finally {
      if (bloqueo.isHeldByCurrentThread()) {
        bloqueo.unlock();
      }
      var retenidoMs = Duration.ofNanos(System.nanoTime() - retenidoDesde).toMillis();
      log.atInfo()
          .addKeyValue("lockName", nombre)
          .addKeyValue("heldMs", retenidoMs)
          .log("Bloqueo %s liberado tras retenerlo %d ms".formatted(nombre, retenidoMs));
    }
  }

  /** {@code idem}: el prefijo anterior a los primeros dos puntos, nunca el nombre completo. */
  private static String tipoDe(String nombre) {
    var dosPuntos = nombre.indexOf(':');
    return dosPuntos < 0 ? nombre : nombre.substring(0, dosPuntos);
  }
}
