package com.roshka.challenge.inventario.dominio;

import java.util.List;
import java.util.UUID;

/** Respuesta a un comando ReservarStock, derivada del estado actual de la reserva. */
public sealed interface RespuestaReserva {

  record Confirmado(List<LineaReserva> lineas) implements RespuestaReserva {}

  record Rechazado(MotivoRechazo motivo, List<UUID> idsProducto) implements RespuestaReserva {}
}
