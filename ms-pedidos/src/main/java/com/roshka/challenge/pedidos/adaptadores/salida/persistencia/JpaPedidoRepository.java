package com.roshka.challenge.pedidos.adaptadores.salida.persistencia;

import com.roshka.challenge.pedidos.aplicacion.consulta.Pagina;
import com.roshka.challenge.pedidos.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaPedidoRepository implements PedidoRepository {

  private final EntityManager entityManager;

  JpaPedidoRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public void insertar(Pedido pedido) {
    entityManager.persist(PedidoEntity.desde(pedido));
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID idPedido) {
    return Optional.ofNullable(entityManager.find(PedidoEntity.class, idPedido))
        .map(PedidoEntity::aDominio);
  }

  /**
   * Trabaja sobre la entidad cargada por {@link #buscarPorId} en la misma transacción, así que la
   * verificación de versión compara contra lo que leyó el caso de uso. Un cambio de estado
   * incrementa la versión mediante el UPDATE versionado; cuando solo se registran comandos (una
   * compensación sobre un pedido terminal) el incremento se fuerza. De cualquier modo, todo paso
   * que escribe en el outbox entra en conflicto con cualquier paso superpuesto sobre el mismo
   * pedido (ADR 0003).
   */
  @Override
  public void actualizar(Pedido pedido) {
    var entidad = entityManager.find(PedidoEntity.class, pedido.id());
    if (!entidad.copiarEstadoDe(pedido)) {
      entityManager.lock(entidad, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
  }

  @Override
  public Pagina<Pedido> buscarPorCreacion(Instant desde, Instant hasta, SolicitudPagina solicitud) {
    var total =
        entityManager
            .createQuery(
                """
                select count(o) from PedidoEntity o
                 where o.creadoEn >= :desde and o.creadoEn < :hasta
                """,
                Long.class)
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .getSingleResult();
    if (solicitud.desplazamiento() >= total) {
      return new Pagina<>(List.of(), solicitud.pagina(), solicitud.tamanio(), total);
    }
    var ids =
        entityManager
            .createQuery(
                """
                select o.id from PedidoEntity o
                 where o.creadoEn >= :desde and o.creadoEn < :hasta
                 order by o.creadoEn desc, o.id desc
                """,
                UUID.class)
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .setFirstResult((int) solicitud.desplazamiento())
            .setMaxResults(solicitud.tamanio())
            .getResultList();
    var porId = new HashMap<UUID, PedidoEntity>();
    entityManager
        .createQuery(
            "select distinct o from PedidoEntity o left join fetch o.lineas where o.id in :ids",
            PedidoEntity.class)
        .setParameter("ids", ids)
        .getResultList()
        .forEach(entidad -> porId.put(entidad.id(), entidad));
    var items = ids.stream().map(id -> porId.get(id).aDominio()).toList();
    return new Pagina<>(items, solicitud.pagina(), solicitud.tamanio(), total);
  }

  @Override
  public List<UUID> buscarPendientesIniciadosAntes(Instant umbral, int limite) {
    return entityManager
        .createQuery(
            """
            select o.id from PedidoEntity o
             where o.estado = :status and o.sagaIniciadaEn < :threshold
             order by o.sagaIniciadaEn
            """,
            UUID.class)
        .setParameter("status", EstadoPedido.PENDING)
        .setParameter("threshold", umbral)
        .setMaxResults(limite)
        .getResultList();
  }
}
