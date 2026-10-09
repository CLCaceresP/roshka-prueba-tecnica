package com.roshka.challenge.inventario.aplicacion.puertos.salida;

import com.roshka.challenge.inventario.dominio.Reserva;
import java.util.Optional;
import java.util.UUID;

/**
 * La fila de reservas de un pedido es el punto de serialización de todos los mensajes de ese
 * pedido: {@link #buscarParaActualizar} bloquea una fila existente e {@link #insertar} falla ante
 * un insert concurrente del mismo pedido (ADR 0010).
 */
public interface ReservaRepository {

  Optional<Reserva> buscarParaActualizar(UUID idPedido);

  void insertar(Reserva reserva);

  void actualizarEstado(Reserva reserva);
}
