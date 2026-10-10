package com.roshka.challenge.inventario.adaptadores.salida.persistencia;

import com.roshka.challenge.inventario.aplicacion.puertos.salida.LibroStock;
import com.roshka.challenge.inventario.dominio.LineaReserva;
import com.roshka.challenge.inventario.dominio.MotivoRechazo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Las actualizaciones condicionales son la garantía contra la sobreventa (ADR 0009): una línea se
 * reserva solo si la fila todavía tiene unidades disponibles suficientes en el momento del UPDATE,
 * hagan lo que hagan las demás instancias.
 */
@Repository
class JdbcLibroStock implements LibroStock {

  private final JdbcClient jdbc;

  JdbcLibroStock(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ResultadoLibro reservar(List<LineaReserva> lineasEnOrdenDeBloqueo) {
    var disponible = disponiblePorProducto(lineasEnOrdenDeBloqueo);
    var desconocido =
        lineasEnOrdenDeBloqueo.stream()
            .map(LineaReserva::idProducto)
            .filter(id -> !disponible.containsKey(id))
            .toList();
    if (!desconocido.isEmpty()) {
      return new Rechazado(
          MotivoRechazo.UNKNOWN_PRODUCT,
          desconocido,
          lineasEnOrdenDeBloqueo.stream()
              .filter(linea -> desconocido.contains(linea.idProducto()))
              .map(linea -> new Faltante(linea.idProducto(), linea.cantidad(), null))
              .toList());
    }
    var aplicado = new ArrayList<LineaReserva>();
    for (var linea : lineasEnOrdenDeBloqueo) {
      if (!tomar(linea)) {
        liberar(aplicado);
        var faltantes = faltantes(lineasEnOrdenDeBloqueo, linea);
        return new Rechazado(
            MotivoRechazo.INSUFFICIENT_STOCK,
            faltantes.stream().map(Faltante::idProducto).toList(),
            faltantes);
      }
      aplicado.add(linea);
    }
    return new Aplicado();
  }

  @Override
  public void liberar(List<LineaReserva> lineasEnOrdenDeBloqueo) {
    for (var linea : lineasEnOrdenDeBloqueo) {
      var actualizado =
          jdbc.sql(
                  """
                  update products
                     set available = available + :qty, reserved = reserved - :qty
                   where id = :id
                  """)
              .param("qty", linea.cantidad())
              .param("id", linea.idProducto())
              .update();
      if (actualizado != 1) {
        throw new IllegalStateException(
            "No se puede devolver stock al producto " + linea.idProducto());
      }
    }
  }

  private boolean tomar(LineaReserva linea) {
    return jdbc.sql(
                """
                update products
                   set available = available - :qty, reserved = reserved + :qty
                 where id = :id and available >= :qty
                """)
            .param("qty", linea.cantidad())
            .param("id", linea.idProducto())
            .update()
        == 1;
  }

  /**
   * Líneas que no caben ahora, con lo pedido y lo disponible; siempre incluye la línea cuyo UPDATE
   * no afectó ninguna fila.
   */
  private List<Faltante> faltantes(List<LineaReserva> lineas, LineaReserva fallido) {
    var disponible = disponiblePorProducto(lineas);
    return lineas.stream()
        .filter(
            linea -> linea.equals(fallido) || disponible.get(linea.idProducto()) < linea.cantidad())
        .map(
            linea ->
                new Faltante(
                    linea.idProducto(), linea.cantidad(), disponible.get(linea.idProducto())))
        .toList();
  }

  private Map<UUID, Integer> disponiblePorProducto(List<LineaReserva> lineas) {
    var ids = lineas.stream().map(LineaReserva::idProducto).toList();
    var disponible = new HashMap<UUID, Integer>();
    jdbc.sql("select id, available from products where id in (:ids)")
        .param("ids", ids)
        .query(
            (RowCallbackHandler)
                rs -> disponible.put(rs.getObject("id", UUID.class), rs.getInt("available")));
    return disponible;
  }
}
