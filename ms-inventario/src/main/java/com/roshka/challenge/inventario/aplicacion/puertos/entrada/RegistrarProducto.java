package com.roshka.challenge.inventario.aplicacion.puertos.entrada;

import com.roshka.challenge.inventario.dominio.Producto;

public interface RegistrarProducto {

  /** Crea un producto cuya cantidad inicial queda toda disponible. */
  Producto registrar(String nombre, int cantidadInicial);
}
