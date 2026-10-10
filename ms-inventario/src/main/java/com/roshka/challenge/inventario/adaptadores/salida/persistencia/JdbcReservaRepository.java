package com.roshka.challenge.inventario.adaptadores.salida.persistencia;

import com.roshka.challenge.inventario.aplicacion.puertos.salida.ReservaRepository;
import com.roshka.challenge.inventario.dominio.EstadoReserva;
import com.roshka.challenge.inventario.dominio.LineaReserva;
import com.roshka.challenge.inventario.dominio.MotivoRechazo;
import com.roshka.challenge.inventario.dominio.Reserva;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReservaRepository implements ReservaRepository {

  private final JdbcClient jdbc;
  private final Clock reloj;

  JdbcReservaRepository(JdbcClient jdbc, Clock reloj) {
    this.jdbc = jdbc;
    this.reloj = reloj;
  }

  @Override
  public Optional<Reserva> buscarParaActualizar(UUID idPedido) {
    return jdbc.sql(
            """
            select order_id, status, rejection_reason, rejected_product_ids
              from reservations where order_id = :orderId for update
            """)
        .param("orderId", idPedido)
        .query((rs, n) -> restaurar(rs))
        .optional();
  }

  @Override
  public void insertar(Reserva reserva) {
    var ahora = OffsetDateTime.now(reloj);
    var motivo = reserva.motivoRechazo();
    jdbc.sql(
            """
            insert into reservations
              (order_id, status, rejection_reason, rejected_product_ids, created_at, updated_at)
            values (:orderId, :status, :reason, :rejectedIds, :now, :now)
            """)
        .param("orderId", reserva.idPedido())
        .param("status", reserva.estado().name())
        .param("reason", motivo == null ? null : motivo.name())
        .param("rejectedIds", new SqlArrayValue("uuid", reserva.idsProductoRechazados().toArray()))
        .param("now", ahora)
        .update();
    for (var linea : reserva.lineas()) {
      jdbc.sql(
              """
              insert into reservation_lines (order_id, product_id, quantity)
              values (:orderId, :productId, :quantity)
              """)
          .param("orderId", reserva.idPedido())
          .param("productId", linea.idProducto())
          .param("quantity", linea.cantidad())
          .update();
    }
  }

  @Override
  public void actualizarEstado(Reserva reserva) {
    jdbc.sql("update reservations set status = :status, updated_at = :now where order_id = :id")
        .param("status", reserva.estado().name())
        .param("now", OffsetDateTime.now(reloj))
        .param("id", reserva.idPedido())
        .update();
  }

  private Reserva restaurar(ResultSet rs) throws SQLException {
    var idPedido = rs.getObject("order_id", UUID.class);
    var motivo = rs.getString("rejection_reason");
    var idsRechazados = (UUID[]) rs.getArray("rejected_product_ids").getArray();
    return Reserva.restaurar(
        idPedido,
        EstadoReserva.valueOf(rs.getString("status")),
        lineasDe(idPedido),
        motivo == null ? null : MotivoRechazo.valueOf(motivo),
        Arrays.asList(idsRechazados));
  }

  private List<LineaReserva> lineasDe(UUID idPedido) {
    return jdbc.sql("select product_id, quantity from reservation_lines where order_id = :orderId")
        .param("orderId", idPedido)
        .query(
            (rs, n) ->
                new LineaReserva(rs.getObject("product_id", UUID.class), rs.getInt("quantity")))
        .list();
  }
}
