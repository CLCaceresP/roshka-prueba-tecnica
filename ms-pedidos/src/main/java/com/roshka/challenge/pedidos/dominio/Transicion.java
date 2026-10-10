package com.roshka.challenge.pedidos.dominio;

/**
 * Resultado de una entrada en un estado dado: el estado destino y, cuando hay que liberar el stock
 * que retiene inventario, por qué.
 */
public record Transicion(EstadoPedido objetivo, CausaLiberacion liberar) {

  static Transicion hasta(EstadoPedido objetivo) {
    return new Transicion(objetivo, null);
  }

  Transicion liberando(CausaLiberacion causa) {
    return new Transicion(objetivo, causa);
  }
}
