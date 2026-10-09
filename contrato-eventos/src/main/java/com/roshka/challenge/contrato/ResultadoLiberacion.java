package com.roshka.challenge.contrato;

public enum ResultadoLiberacion {
  /** Una reserva RESERVED se liberó y su stock se devolvió. */
  DEVUELTO,
  /** No existía reserva: se registró un tombstone RELEASED sin tocar el stock. */
  TOMBSTONE_REGISTRADO,
  /** La reserva ya estaba RELEASED o REJECTED: no cambió nada. */
  NADA_QUE_LIBERAR
}
