package com.roshka.challenge.pedidos.aplicacion.puertos.salida;

import com.roshka.challenge.contrato.SobreMensaje;

/** Outbox transaccional: el mensaje se publica solo si la transacción actual commitea. */
public interface OutboxMensajes {

  void agregar(String topico, SobreMensaje<?> sobre);
}
