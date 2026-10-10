package com.roshka.challenge.pedidos.adaptadores.salida.persistencia;

import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.MotivoRechazoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Modelo de persistencia del agregado Pedido; el modelo de dominio queda libre de JPA. */
@Entity
@Table(name = "orders")
class PedidoEntity {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private EstadoPedido estado;

  @Enumerated(EnumType.STRING)
  @Column(name = "rejection_reason")
  private MotivoRechazoPedido motivoRechazo;

  @Column(name = "correlation_id", nullable = false)
  private String idCorrelacion;

  @Column(name = "saga_started_at", nullable = false)
  private Instant sagaIniciadaEn;

  @Column(name = "created_at", nullable = false)
  private Instant creadoEn;

  @Column(name = "updated_at", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(name = "version")
  private Long version;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
  private List<LineaPedidoValor> lineas = new ArrayList<>();

  protected PedidoEntity() {}

  static PedidoEntity desde(Pedido pedido) {
    var entidad = new PedidoEntity();
    entidad.id = pedido.id();
    entidad.idCorrelacion = pedido.idCorrelacion();
    entidad.sagaIniciadaEn = pedido.sagaIniciadaEn();
    entidad.creadoEn = pedido.creadoEn();
    entidad.lineas =
        new ArrayList<>(
            pedido.lineas().stream()
                .map(linea -> new LineaPedidoValor(linea.idProducto(), linea.cantidad()))
                .toList());
    entidad.copiarEstadoDe(pedido);
    return entidad;
  }

  /**
   * @return si cambió el estado persistido
   */
  boolean copiarEstadoDe(Pedido pedido) {
    var cambiado = estado != pedido.estado() || motivoRechazo != pedido.motivoRechazo();
    estado = pedido.estado();
    motivoRechazo = pedido.motivoRechazo();
    actualizadoEn = pedido.actualizadoEn();
    return cambiado;
  }

  UUID id() {
    return id;
  }

  Pedido aDominio() {
    return Pedido.restaurar(
        id,
        estado,
        motivoRechazo,
        lineas.stream()
            .map(linea -> new LineaPedido(linea.idProducto(), linea.cantidad()))
            .toList(),
        idCorrelacion,
        creadoEn,
        actualizadoEn,
        sagaIniciadaEn);
  }
}
