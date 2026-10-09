package com.roshka.challenge.inventario.aplicacion.puertos.salida;

import com.roshka.challenge.inventario.dominio.LineaReserva;
import com.roshka.challenge.inventario.dominio.MotivoRechazo;
import java.util.List;
import java.util.UUID;

/**
 * Mueve unidades entre disponible y reservado; la base de datos garantiza que no haya sobreventa
 * (ADR 0009).
 */
public interface LibroStock {

  /**
   * Aplica todas las líneas o ninguna. Las líneas deben venir en orden de bloqueo (idProducto
   * ascendente).
   */
  ResultadoLibro reservar(List<LineaReserva> lineasEnOrdenDeBloqueo);

  /** Devuelve las unidades reservadas a disponible. Las líneas deben venir en orden de bloqueo. */
  void liberar(List<LineaReserva> lineasEnOrdenDeBloqueo);

  sealed interface ResultadoLibro {}

  record Aplicado() implements ResultadoLibro {}

  /**
   * @param faltantes qué se pidió y qué había por cada producto que no alcanzó o no existe, para
   *     poder explicar el rechazo
   */
  record Rechazado(MotivoRechazo motivo, List<UUID> idsProducto, List<Faltante> faltantes)
      implements ResultadoLibro {}

  /**
   * @param disponible unidades que había al decidir; {@code null} si el producto no existe
   */
  record Faltante(UUID idProducto, int solicitado, Integer disponible) {}
}
