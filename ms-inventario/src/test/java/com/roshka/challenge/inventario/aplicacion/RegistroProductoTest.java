package com.roshka.challenge.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.inventario.dominio.ProductoDuplicado;
import com.roshka.challenge.inventario.dominio.ProductoNoEncontrado;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegistroProductoTest {

  private final InventarioEnMemoria inventario = new InventarioEnMemoria();

  @Test
  @DisplayName("REQ-22 · 001-AC-01 registrar guarda un producto con todo el stock disponible")
  void debe_guardar_el_producto_con_id_generado_cuando_se_registra() {
    var producto = new RegistroProducto(inventario).registrar("Keyboard", 10);

    assertThat(inventario.stock(producto.id())).isEqualTo(producto);
    assertThat(producto.disponible()).isEqualTo(10);
  }

  @Test
  @DisplayName("REQ-23 · 001-AC-02 la consulta de stock devuelve el stock guardado")
  void debe_devolver_el_stock_cuando_el_producto_existe() {
    var producto = new RegistroProducto(inventario).registrar("Keyboard", 10);

    assertThat(new ConsultaStockProducto(inventario).stockDe(producto.id())).isEqualTo(producto);
  }

  @Test
  @DisplayName("REQ-23 · 001-AC-03 un producto desconocido se informa como no encontrado")
  void debe_fallar_con_no_encontrado_cuando_el_producto_no_existe() {
    var desconocido = UUID.randomUUID();

    assertThatThrownBy(() -> new ConsultaStockProducto(inventario).stockDe(desconocido))
        .isInstanceOf(ProductoNoEncontrado.class)
        .hasMessageContaining(desconocido.toString());
  }

  @Test
  @DisplayName(
      "REQ-22 · 011-AC-01/02 un nombre repetido se rechaza sin distinguir mayúsculas ni espacios")
  void debe_rechazar_el_producto_cuando_el_nombre_ya_existe_en_otra_forma() {
    var existente = new RegistroProducto(inventario).registrar("Teclado Gamer", 10);

    assertThatThrownBy(() -> new RegistroProducto(inventario).registrar("  teclado   GAMER ", 3))
        .isInstanceOfSatisfying(
            ProductoDuplicado.class,
            e -> {
              assertThat(e.idExistente()).isEqualTo(existente.id());
              assertThat(e).hasMessageContaining(existente.id().toString());
            });
    assertThat(inventario.productos).hasSize(1);
  }
}
