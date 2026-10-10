package com.roshka.challenge.pedidos.aplicacion.puertos.salida;

import com.roshka.challenge.pedidos.aplicacion.consulta.Pagina;
import com.roshka.challenge.pedidos.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository {

  void insertar(Pedido pedido);

  Optional<Pedido> buscarPorId(UUID idPedido);

  /**
   * Persiste el pedido cargado en la transacción actual e incrementa su versión, para que dos
   * transacciones superpuestas sobre el mismo pedido no puedan commitear ambas (ADR 0003, bloqueo
   * optimista).
   */
  void actualizar(Pedido pedido);

  List<UUID> buscarPendientesIniciadosAntes(Instant umbral, int limite);

  /**
   * Pedidos creados en {@code [desde, hasta)}, del más reciente al más antiguo y, a igualdad de
   * fecha, por id descendente.
   */
  Pagina<Pedido> buscarPorCreacion(Instant desde, Instant hasta, SolicitudPagina solicitud);
}
