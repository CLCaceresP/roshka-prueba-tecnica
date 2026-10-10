package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoDistribuido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoNoAdquirido;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Concede todos los bloqueos salvo los marcados como retenidos por otro, y registra lo que se
 * pidió.
 */
public class BloqueoRegistrador implements BloqueoDistribuido {

  public final Set<String> retenidoPorOtro = new HashSet<>();
  public final List<String> adquirido = new ArrayList<>();

  @Override
  public <T> T llamarConBloqueo(
      String nombre, Duration espera, Duration lease, Supplier<T> accion) {
    if (retenidoPorOtro.contains(nombre)) {
      throw new BloqueoNoAdquirido(nombre);
    }
    adquirido.add(nombre);
    return accion.get();
  }
}
