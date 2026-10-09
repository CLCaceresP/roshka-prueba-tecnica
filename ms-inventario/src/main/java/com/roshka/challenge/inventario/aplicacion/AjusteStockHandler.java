package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.inventario.aplicacion.puertos.entrada.AjustarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MovimientosStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.dominio.AjusteStock;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoNoEncontrado;
import com.roshka.challenge.inventario.dominio.TipoAjuste;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La fila del producto queda bloqueada desde que se lee hasta que commitea, así que la diferencia
 * del movimiento es exacta. El producto decide el stock nuevo y el tope; la base lo respalda con un
 * {@code CHECK} (ADR 0022).
 */
@Service
public class AjusteStockHandler implements AjustarStock {

  private static final Logger log = LoggerFactory.getLogger(AjusteStockHandler.class);

  private final ProductoRepository productos;
  private final MovimientosStock movimientos;
  private final Clock reloj;

  public AjusteStockHandler(
      ProductoRepository productos, MovimientosStock movimientos, Clock reloj) {
    this.productos = productos;
    this.movimientos = movimientos;
    this.reloj = reloj;
  }

  @Override
  @Transactional
  public Resultado ajustar(AjusteStock ajuste) {
    var pasos =
        Pasos.proceso(AjusteStockHandler.class, "Ajustar stock")
            .con("productId", ajuste.idProducto())
            .con("available", ajuste.disponible());
    return pasos.ejecutar(
        () -> {
          var actual =
              pasos.medir(
                  "Producto bloqueado para actualizar",
                  () ->
                      productos
                          .bloquearPorId(ajuste.idProducto())
                          .orElseThrow(() -> new ProductoNoEncontrado(ajuste.idProducto())));
          var nuevo =
              pasos.medir("Stock nuevo calculado", () -> actual.conDisponible(ajuste.disponible()));
          var movimiento = MovimientoStock.entre(actual, nuevo, ajuste.motivo(), reloj.instant());
          if (movimiento.isPresent()) {
            pasos.medir("Stock fijado en la base de datos", () -> productos.actualizarStock(nuevo));
            pasos.medir("Movimiento registrado", () -> movimientos.insertar(movimiento.get()));
          }
          registrar(actual.disponible(), nuevo.disponible(), nuevo, movimiento.orElse(null));
          return new Resultado(nuevo, movimiento.orElse(null));
        });
  }

  private void registrar(int antes, int despues, Producto producto, MovimientoStock movimiento) {
    var evento =
        log.atInfo()
            .addKeyValue("productId", producto.id().toString())
            .addKeyValue("availableBefore", antes)
            .addKeyValue("availableAfter", despues)
            .addKeyValue("totalAfter", producto.total());
    if (movimiento == null) {
      evento.log(
          "Stock del producto %s sin cambios: ya había %d disponibles"
              .formatted(producto.id(), despues));
      return;
    }
    evento
        .addKeyValue("movementId", movimiento.id().toString())
        .addKeyValue("type", movimiento.tipo().name())
        .addKeyValue("quantity", movimiento.cantidad())
        .log(
            "Stock del producto %s fijado en %d disponibles (antes %d): se %s %d unidades"
                .formatted(
                    producto.id(),
                    despues,
                    antes,
                    movimiento.tipo() == TipoAjuste.ADD ? "agregaron" : "retiraron",
                    movimiento.cantidad()));
  }
}
