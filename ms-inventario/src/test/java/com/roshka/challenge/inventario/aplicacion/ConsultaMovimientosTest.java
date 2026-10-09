package com.roshka.challenge.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.inventario.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoNoEncontrado;
import com.roshka.challenge.inventario.dominio.TipoAjuste;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultaMovimientosTest {

  private final InventarioEnMemoria inventario = new InventarioEnMemoria();
  private final ConsultaMovimientos consulta =
      new ConsultaMovimientos(inventario, inventario, new ConfiguracionPaginacion(20, 100));

  @Test
  @DisplayName("EXT-03 · 014-AC-09 el historial va del más reciente al más antiguo y se pagina")
  void debe_devolver_los_movimientos_mas_recientes_primero_cuando_se_pagina() {
    var producto = Producto.registrar(UUID.randomUUID(), "con-historial", 10);
    inventario.insertar(producto);
    for (int i = 1; i <= 5; i++) {
      inventario.insertar(
          new MovimientoStock(
              UUID.randomUUID(),
              producto.id(),
              TipoAjuste.ADD,
              i,
              null,
              10 + i,
              10 + i,
              Instant.parse("2026-10-10T12:00:0%dZ".formatted(i))));
    }

    var pagina = consulta.listar(producto.id(), 0, 2);

    assertThat(pagina.items()).extracting(MovimientoStock::cantidad).containsExactly(5, 4);
    assertThat(pagina.totalElementos()).isEqualTo(5);
    assertThat(pagina.totalPaginas()).isEqualTo(3);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-05 el historial de un producto desconocido es no encontrado")
  void debe_fallar_con_no_encontrado_cuando_el_producto_del_historial_no_existe() {
    var desconocido = UUID.randomUUID();

    assertThatThrownBy(() -> consulta.listar(desconocido, 0, 20))
        .isInstanceOf(ProductoNoEncontrado.class)
        .hasMessageContaining(desconocido.toString());
  }
}
