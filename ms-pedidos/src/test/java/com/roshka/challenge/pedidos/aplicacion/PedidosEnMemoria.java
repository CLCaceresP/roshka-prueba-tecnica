package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.pedidos.aplicacion.consulta.Pagina;
import com.roshka.challenge.pedidos.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.MensajesProcesados;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.MetricasPedidos;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.OutboxMensajes;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementación en memoria de los puertos de salida, con la misma semántica que la base de datos.
 */
public class PedidosEnMemoria
    implements PedidoRepository, OutboxMensajes, MensajesProcesados, MetricasPedidos {

  public record Publicado(String topico, SobreMensaje<?> sobre) {}

  public final Map<UUID, Pedido> pedidos = new HashMap<>();
  public final Map<UUID, Integer> versiones = new HashMap<>();
  public final Set<UUID> procesado = new HashSet<>();
  public final List<Publicado> outbox = new ArrayList<>();
  public final List<EstadoPedido> terminado = new ArrayList<>();

  @Override
  public void insertar(Pedido pedido) {
    if (pedidos.putIfAbsent(pedido.id(), pedido) != null) {
      throw new IllegalStateException("clave duplicada: pedido " + pedido.id());
    }
    versiones.put(pedido.id(), 0);
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID idPedido) {
    return Optional.ofNullable(pedidos.get(idPedido));
  }

  @Override
  public void actualizar(Pedido pedido) {
    pedidos.put(pedido.id(), pedido);
    versiones.merge(pedido.id(), 1, Integer::sum);
  }

  @Override
  public List<UUID> buscarPendientesIniciadosAntes(Instant umbral, int limite) {
    return pedidos.values().stream()
        .filter(o -> o.estado() == EstadoPedido.PENDING && o.sagaIniciadaEn().isBefore(umbral))
        .sorted(Comparator.comparing(Pedido::sagaIniciadaEn))
        .limit(limite)
        .map(Pedido::id)
        .toList();
  }

  @Override
  public Pagina<Pedido> buscarPorCreacion(Instant desde, Instant hasta, SolicitudPagina solicitud) {
    var ordenados =
        pedidos.values().stream()
            .filter(o -> !o.creadoEn().isBefore(desde) && o.creadoEn().isBefore(hasta))
            .sorted(Comparator.comparing(Pedido::creadoEn).thenComparing(Pedido::id).reversed())
            .toList();
    var primero = (int) Math.min(solicitud.desplazamiento(), ordenados.size());
    var ultimo = Math.min(primero + solicitud.tamanio(), ordenados.size());
    return new Pagina<>(
        ordenados.subList(primero, ultimo),
        solicitud.pagina(),
        solicitud.tamanio(),
        ordenados.size());
  }

  @Override
  public void agregar(String topico, SobreMensaje<?> sobre) {
    outbox.add(new Publicado(topico, sobre));
  }

  @Override
  public boolean marcarProcesado(UUID idMensaje) {
    return procesado.add(idMensaje);
  }

  @Override
  public void pedidoColocado() {}

  @Override
  public void pedidoTerminado(
      EstadoPedido estado, MotivoRechazoPedido motivo, Duration duracionSaga) {
    terminado.add(estado);
  }
}
