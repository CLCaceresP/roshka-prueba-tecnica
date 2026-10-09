package com.roshka.challenge.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.roshka.challenge.inventario.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.inventario.dominio.Producto;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultaProductosTest {

  private final InventarioEnMemoria inventario = new InventarioEnMemoria();
  private final ConsultaProductos consulta =
      new ConsultaProductos(inventario, new ConfiguracionPaginacion(20, 100));

  @Test
  @DisplayName("EXT-01 · 012-AC-01 el listado devuelve la página pedida con el total")
  void debe_devolver_la_pagina_y_el_total_cuando_hay_mas_productos_que_el_tamano() {
    for (int i = 0; i < 5; i++) {
      inventario.insertar(Producto.registrar(UUID.randomUUID(), "producto-" + i, 1));
    }

    var pagina = consulta.listar(1, 2);

    assertThat(pagina.items())
        .extracting(Producto::nombre)
        .containsExactly("producto-2", "producto-3");
    assertThat(pagina.totalElementos()).isEqualTo(5);
    assertThat(pagina.totalPaginas()).isEqualTo(3);
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-04 una página fuera de rango devuelve items vacíos y el total")
  void debe_devolver_items_vacios_cuando_la_pagina_esta_fuera_de_rango() {
    inventario.insertar(Producto.registrar(UUID.randomUUID(), "unico", 1));

    var pagina = consulta.listar(5, 20);

    assertThat(pagina.items()).isEmpty();
    assertThat(pagina.totalElementos()).isEqualTo(1);
  }

  @Test
  @DisplayName("EXT-01 · 012-AC-05 el orden ignora las mayúsculas")
  void debe_ordenar_por_nombre_sin_distinguir_mayusculas() {
    inventario.insertar(Producto.registrar(UUID.randomUUID(), "beta", 1));
    inventario.insertar(Producto.registrar(UUID.randomUUID(), "Alfa", 1));
    inventario.insertar(Producto.registrar(UUID.randomUUID(), "Gamma", 1));

    var pagina = consulta.listar(0, 10);

    assertThat(pagina.items())
        .extracting(Producto::nombre)
        .containsExactly("Alfa", "beta", "Gamma");
  }
}
