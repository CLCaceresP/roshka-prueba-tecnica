package com.roshka.challenge.inventario.adaptadores.salida.persistencia;

import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoDuplicado;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProductoRepository implements ProductoRepository {

  private static final String INDICE_NOMBRE_UNICO = "products_name_unique";

  private final JdbcClient jdbc;
  private final Clock reloj;

  JdbcProductoRepository(JdbcClient jdbc, Clock reloj) {
    this.jdbc = jdbc;
    this.reloj = reloj;
  }

  @Override
  public void insertar(Producto producto) {
    try {
      jdbc.sql(
              """
              insert into products (id, name, total, available, reserved, created_at)
              values (:id, :name, :total, :available, :reserved, :createdAt)
              """)
          .param("id", producto.id())
          .param("name", producto.nombre())
          .param("total", producto.total())
          .param("available", producto.disponible())
          .param("reserved", producto.reservado())
          .param("createdAt", OffsetDateTime.now(reloj))
          .update();
    } catch (DuplicateKeyException e) {
      if (e.getMessage() != null && e.getMessage().contains(INDICE_NOMBRE_UNICO)) {
        throw new ProductoDuplicado(producto.nombre(), null);
      }
      throw e;
    }
  }

  @Override
  public Optional<Producto> buscarPorNombre(String nombreNormalizado) {
    return jdbc.sql(
            """
            select id, name, total, available, reserved from products
             where lower(btrim(name)) = lower(btrim(:name))
            """)
        .param("name", nombreNormalizado)
        .query(JdbcProductoRepository::aProducto)
        .optional();
  }

  @Override
  public Optional<Producto> bloquearPorId(UUID idProducto) {
    return jdbc.sql(
            "select id, name, total, available, reserved from products where id = :id for update")
        .param("id", idProducto)
        .query(JdbcProductoRepository::aProducto)
        .optional();
  }

  @Override
  public void actualizarStock(Producto producto) {
    jdbc.sql(
            """
            update products set total = :total, available = :available, reserved = :reserved
             where id = :id
            """)
        .param("total", producto.total())
        .param("available", producto.disponible())
        .param("reserved", producto.reservado())
        .param("id", producto.id())
        .update();
  }

  @Override
  public Pagina<Producto> listar(SolicitudPagina solicitud) {
    var total = jdbc.sql("select count(*) from products").query(Long.class).single();
    var items =
        jdbc.sql(
                """
                select id, name, total, available, reserved from products
                 order by lower(btrim(name)), id
                 limit :limite offset :desplazamiento
                """)
            .param("limite", solicitud.tamanio())
            .param("desplazamiento", solicitud.desplazamiento())
            .query(JdbcProductoRepository::aProducto)
            .list();
    return new Pagina<>(items, solicitud.pagina(), solicitud.tamanio(), total);
  }

  @Override
  public Optional<Producto> buscarPorId(UUID idProducto) {
    return jdbc.sql("select id, name, total, available, reserved from products where id = :id")
        .param("id", idProducto)
        .query(JdbcProductoRepository::aProducto)
        .optional();
  }

  private static Producto aProducto(ResultSet rs, int fila) throws SQLException {
    return new Producto(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getInt("total"),
        rs.getInt("available"),
        rs.getInt("reserved"));
  }
}
