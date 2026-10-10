package com.roshka.challenge.inventario.adaptadores.salida.persistencia;

import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MovimientosStock;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.TipoAjuste;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMovimientosStock implements MovimientosStock {

  private final JdbcClient jdbc;

  JdbcMovimientosStock(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insertar(MovimientoStock movimiento) {
    jdbc.sql(
            """
            insert into stock_movements
              (id, product_id, type, quantity, reason, total_after, available_after, created_at)
            values
              (:id, :productId, :type, :quantity, :reason, :totalAfter, :availableAfter, :createdAt)
            """)
        .param("id", movimiento.id())
        .param("productId", movimiento.idProducto())
        .param("type", movimiento.tipo().name())
        .param("quantity", movimiento.cantidad())
        .param("reason", movimiento.motivo())
        .param("totalAfter", movimiento.totalDespues())
        .param("availableAfter", movimiento.disponibleDespues())
        .param("createdAt", OffsetDateTime.ofInstant(movimiento.creadoEn(), ZoneOffset.UTC))
        .update();
  }

  @Override
  public Pagina<MovimientoStock> listar(UUID idProducto, SolicitudPagina solicitud) {
    var total =
        jdbc.sql("select count(*) from stock_movements where product_id = :id")
            .param("id", idProducto)
            .query(Long.class)
            .single();
    var items =
        jdbc.sql(
                """
                select id, product_id, type, quantity, reason, total_after, available_after, created_at
                  from stock_movements
                 where product_id = :id
                 order by created_at desc, id desc
                 limit :limite offset :desplazamiento
                """)
            .param("id", idProducto)
            .param("limite", solicitud.tamanio())
            .param("desplazamiento", solicitud.desplazamiento())
            .query(JdbcMovimientosStock::aMovimiento)
            .list();
    return new Pagina<>(items, solicitud.pagina(), solicitud.tamanio(), total);
  }

  private static MovimientoStock aMovimiento(ResultSet rs, int fila) throws SQLException {
    return new MovimientoStock(
        rs.getObject("id", UUID.class),
        rs.getObject("product_id", UUID.class),
        TipoAjuste.valueOf(rs.getString("type")),
        rs.getInt("quantity"),
        rs.getString("reason"),
        rs.getInt("total_after"),
        rs.getInt("available_after"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
