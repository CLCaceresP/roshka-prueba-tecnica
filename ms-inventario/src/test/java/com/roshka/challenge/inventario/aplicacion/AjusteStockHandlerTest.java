package com.roshka.challenge.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.inventario.dominio.AjusteStock;
import com.roshka.challenge.inventario.dominio.LimiteStockExcedido;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoNoEncontrado;
import com.roshka.challenge.inventario.dominio.TipoAjuste;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AjusteStockHandlerTest {

  private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");

  private final InventarioEnMemoria inventario = new InventarioEnMemoria();
  private final AjusteStockHandler handler =
      new AjusteStockHandler(inventario, inventario, Clock.fixed(AHORA, ZoneOffset.UTC));

  @Test
  @DisplayName("EXT-03 · 014-AC-01 subir lo disponible suma al total y registra un movimiento ADD")
  void debe_registrar_un_movimiento_de_alta_cuando_sube_lo_disponible() {
    var id = producto(5, 0);

    var resultado = handler.ajustar(AjusteStock.de(id, 15, "reposición"));

    assertThat(inventario.stock(id)).isEqualTo(new Producto(id, "p", 15, 15, 0));
    assertThat(resultado.producto()).isEqualTo(inventario.stock(id));
    assertThat(inventario.movimientosGuardados).hasSize(1);
    var movimiento = resultado.movimiento();
    assertThat(movimiento.tipo()).isEqualTo(TipoAjuste.ADD);
    assertThat(movimiento.cantidad()).isEqualTo(10);
    assertThat(movimiento.motivo()).isEqualTo("reposición");
    assertThat(movimiento.totalDespues()).isEqualTo(15);
    assertThat(movimiento.disponibleDespues()).isEqualTo(15);
    assertThat(movimiento.creadoEn()).isEqualTo(AHORA);
    assertThat(movimiento.idProducto()).isEqualTo(id);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-02 bajar lo disponible conserva lo reservado y registra REMOVE")
  void debe_conservar_lo_reservado_cuando_baja_lo_disponible() {
    var id = producto(8, 2);

    var resultado = handler.ajustar(AjusteStock.de(id, 5, null));

    assertThat(inventario.stock(id)).isEqualTo(new Producto(id, "p", 7, 5, 2));
    assertThat(resultado.movimiento().tipo()).isEqualTo(TipoAjuste.REMOVE);
    assertThat(resultado.movimiento().cantidad()).isEqualTo(3);
    assertThat(resultado.movimiento().totalDespues()).isEqualTo(7);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-03 se puede dejar lo disponible en cero aunque haya reservas")
  void debe_dejar_lo_disponible_en_cero_y_conservar_las_reservas() {
    var id = producto(3, 5);

    handler.ajustar(AjusteStock.de(id, 0, null));

    assertThat(inventario.stock(id)).isEqualTo(new Producto(id, "p", 5, 0, 5));
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-04 el total tiene un tope")
  void debe_rechazar_el_ajuste_cuando_el_total_superaria_el_maximo() {
    var id = producto(1, 5);

    assertThatThrownBy(() -> handler.ajustar(AjusteStock.de(id, 10_000_000, null)))
        .isInstanceOf(LimiteStockExcedido.class)
        .hasMessageContaining("5 unidades reservadas")
        .hasMessageContaining("superaría el máximo");

    assertThat(inventario.stock(id)).isEqualTo(new Producto(id, "p", 6, 1, 5));
    assertThat(inventario.movimientosGuardados).isEmpty();
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-06 si no hay cambio no se registra ningún movimiento")
  void debe_responder_sin_movimiento_cuando_lo_disponible_ya_tiene_ese_valor() {
    var id = producto(5, 2);

    var resultado = handler.ajustar(AjusteStock.de(id, 5, null));

    assertThat(resultado.movimiento()).isNull();
    assertThat(resultado.producto()).isEqualTo(new Producto(id, "p", 7, 5, 2));
    assertThat(inventario.movimientosGuardados).isEmpty();
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-05 un producto desconocido se informa como no encontrado")
  void debe_fallar_con_no_encontrado_cuando_el_producto_no_existe() {
    var desconocido = UUID.randomUUID();

    assertThatThrownBy(() -> handler.ajustar(AjusteStock.de(desconocido, 1, null)))
        .isInstanceOf(ProductoNoEncontrado.class)
        .hasMessageContaining(desconocido.toString());
    assertThat(inventario.movimientosGuardados).isEmpty();
  }

  private UUID producto(int disponible, int reservado) {
    var producto =
        new Producto(UUID.randomUUID(), "p", disponible + reservado, disponible, reservado);
    inventario.insertar(producto);
    return producto.id();
  }
}
