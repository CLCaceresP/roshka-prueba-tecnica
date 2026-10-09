package com.roshka.challenge.inventario.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductoTest {

  @Test
  @DisplayName("REQ-10 · 001-AC-01 un producto registrado empieza con todo su stock disponible")
  void debe_dejar_todo_el_stock_inicial_disponible_cuando_se_registra() {
    var producto = Producto.registrar(UUID.randomUUID(), "Keyboard", 10);

    assertThat(producto.total()).isEqualTo(10);
    assertThat(producto.disponible()).isEqualTo(10);
    assertThat(producto.reservado()).isZero();
  }

  @Test
  @DisplayName("REQ-10 · 001 caso borde: un stock inicial cero es válido")
  void debe_aceptar_stock_cero_cuando_se_registra() {
    assertThat(Producto.registrar(UUID.randomUUID(), "Mouse", 0).disponible()).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, Producto.CANTIDAD_INICIAL_MAXIMA + 1})
  @DisplayName("REQ-22 · 001-AC-11 una cantidad inicial fuera de rango se rechaza")
  void debe_rechazar_el_registro_cuando_la_cantidad_inicial_esta_fuera_de_rango(int cantidad) {
    assertThatThrownBy(() -> Producto.registrar(UUID.randomUUID(), "Mouse", cantidad))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("cantidadInicial");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("REQ-22 · 001-AC-11 los nombres en blanco se rechazan")
  void debe_rechazar_el_registro_cuando_el_nombre_esta_en_blanco(String nombre) {
    assertThatThrownBy(() -> Producto.registrar(UUID.randomUUID(), nombre, 1))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("nombre");
  }

  @Test
  @DisplayName("REQ-22 · 001-AC-11 los nombres más largos que el límite se rechazan")
  void debe_rechazar_el_registro_cuando_el_nombre_es_demasiado_largo() {
    var nombre = "x".repeat(Producto.LONGITUD_MAXIMA_NOMBRE + 1);

    assertThatThrownBy(() -> Producto.registrar(UUID.randomUUID(), nombre, 1))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("nombre");
  }

  @Test
  @DisplayName("REQ-13 · 001 RN-01 el stock disponible negativo no es representable")
  void debe_rechazar_el_estado_cuando_disponible_es_negativo() {
    assertThatThrownBy(() -> new Producto(UUID.randomUUID(), "Mouse", 1, -1, 2))
        .isInstanceOf(ProductoInvalido.class);
  }

  @Test
  @DisplayName("REQ-10 · 001 RN-02 disponible más reservado debe ser igual al total")
  void debe_rechazar_el_estado_cuando_el_stock_no_se_conserva() {
    assertThatThrownBy(() -> new Producto(UUID.randomUUID(), "Mouse", 5, 3, 3))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("total");
  }

  @Test
  @DisplayName("REQ-22 · 011-AC-03 el nombre se guarda sin espacios de más")
  void debe_normalizar_el_nombre_cuando_trae_espacios_de_mas() {
    var producto = Producto.registrar(UUID.randomUUID(), "  Teclado \t  Gamer\n ", 1);

    assertThat(producto.nombre()).isEqualTo("Teclado Gamer");
  }

  @Test
  @DisplayName("REQ-22 · 011-AC-04 los caracteres de control se rechazan")
  void debe_rechazar_el_nombre_cuando_trae_caracteres_de_control() {
    assertThatThrownBy(() -> Producto.registrar(UUID.randomUUID(), "Tecla\u0007do", 1))
        .isInstanceOf(ProductoInvalido.class)
        .hasMessageContaining("caracteres de control");
  }

  @Test
  @DisplayName("REQ-22 · 011-AC-03 la longitud se mide sobre el nombre normalizado")
  void debe_aceptar_el_nombre_cuando_solo_excede_el_limite_por_espacios_de_mas() {
    var nombre = " " + "x".repeat(Producto.LONGITUD_MAXIMA_NOMBRE) + "   ";

    assertThat(Producto.registrar(UUID.randomUUID(), nombre, 1).nombre())
        .hasSize(Producto.LONGITUD_MAXIMA_NOMBRE);
  }

  @Test
  @DisplayName(
      "EXT-03 · 014-RN-01 dejar un disponible nuevo recalcula el total y conserva lo reservado")
  void debe_recalcular_el_total_y_conservar_lo_reservado_cuando_cambia_lo_disponible() {
    var producto = new Producto(UUID.randomUUID(), "p", 10, 8, 2);

    var nuevo = producto.conDisponible(0);
    var mayor = producto.conDisponible(50);

    assertThat(nuevo).isEqualTo(new Producto(producto.id(), "p", 2, 0, 2));
    assertThat(mayor).isEqualTo(new Producto(producto.id(), "p", 52, 50, 2));
    assertThat(producto.disponible()).isEqualTo(8);
  }

  @Test
  @DisplayName("EXT-03 · 014-RN-02 el total no puede superar el máximo")
  void debe_rechazar_el_cambio_cuando_el_total_superaria_el_maximo() {
    var producto = new Producto(UUID.randomUUID(), "p", 6, 1, 5);

    assertThat(producto.conDisponible(Producto.TOTAL_MAXIMO - 5).total())
        .isEqualTo(Producto.TOTAL_MAXIMO);
    assertThatThrownBy(() -> producto.conDisponible(Producto.TOTAL_MAXIMO - 4))
        .isInstanceOf(LimiteStockExcedido.class)
        .hasMessageContaining("5 unidades reservadas")
        .hasMessageContaining("superaría el máximo");
  }
}
