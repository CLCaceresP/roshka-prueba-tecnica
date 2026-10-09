package com.roshka.challenge.inventario.aplicacion;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * Cronometra y registra los pasos de un proceso (spec 010, ADR 0019). Cada paso escribe su nombre y
 * su duración al terminar; el proceso escribe su total. Los atributos que el MDC ya aporta no se
 * repiten, porque la consola JSON falla si una clave se escribe dos veces.
 */
public final class Pasos {

  private final Logger log;
  private final String proceso;
  private final Map<String, String> atributos = new LinkedHashMap<>();
  private final long inicio = System.nanoTime();
  private int pasos;

  private Pasos(Logger log, String proceso) {
    this.log = log;
    this.proceso = proceso;
  }

  public static Pasos proceso(Class<?> origen, String proceso) {
    return new Pasos(LoggerFactory.getLogger(origen), proceso);
  }

  public Pasos con(String clave, Object valor) {
    atributos.put(clave, String.valueOf(valor));
    return this;
  }

  public <T> T medir(String paso, Supplier<T> accion) {
    var desde = System.nanoTime();
    try {
      var resultado = accion.get();
      pasos++;
      var ms = milisegundos(desde);
      registrar(Level.INFO, paso + " (" + ms + " ms)", paso, ms, "ok", null);
      return resultado;
    } catch (RuntimeException e) {
      pasos++;
      var ms = milisegundos(desde);
      registrar(Level.WARN, paso + " falló tras " + ms + " ms: " + motivo(e), paso, ms, "error", e);
      throw e;
    }
  }

  public void medir(String paso, Runnable accion) {
    medir(
        paso,
        () -> {
          accion.run();
          return null;
        });
  }

  public <T> T ejecutar(Supplier<T> cuerpo) {
    try {
      var resultado = cuerpo.get();
      var ms = milisegundos(inicio);
      var evento = base(log.atInfo(), null, ms, "ok").addKeyValue("steps", pasos);
      evento.log(proceso + " completado en " + ms + " ms (" + pasos + " pasos)");
      return resultado;
    } catch (RuntimeException e) {
      var ms = milisegundos(inicio);
      base(log.atWarn(), null, ms, "error")
          .addKeyValue("steps", pasos)
          .addKeyValue("error", e.getClass().getSimpleName())
          .log(proceso + " falló tras " + ms + " ms: " + motivo(e));
      throw e;
    }
  }

  private void registrar(
      Level nivel, String mensaje, String paso, long ms, String resultado, RuntimeException error) {
    var evento = base(log.atLevel(nivel), paso, ms, resultado);
    if (error != null) {
      evento = evento.addKeyValue("error", error.getClass().getSimpleName());
    }
    evento.log(mensaje);
  }

  private org.slf4j.spi.LoggingEventBuilder base(
      org.slf4j.spi.LoggingEventBuilder evento, String paso, long ms, String resultado) {
    evento =
        evento
            .addKeyValue("process", proceso)
            .addKeyValue("durationMs", ms)
            .addKeyValue("outcome", resultado);
    if (paso != null) {
      evento = evento.addKeyValue("step", paso);
    }
    for (var atributo : atributos.entrySet()) {
      if (MDC.get(atributo.getKey()) == null) {
        evento = evento.addKeyValue(atributo.getKey(), atributo.getValue());
      }
    }
    return evento;
  }

  private static long milisegundos(long desdeNanos) {
    return Duration.ofNanos(System.nanoTime() - desdeNanos).toMillis();
  }

  private static String motivo(RuntimeException e) {
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }
}
