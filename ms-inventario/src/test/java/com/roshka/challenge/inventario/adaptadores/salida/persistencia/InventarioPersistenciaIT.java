package com.roshka.challenge.inventario.adaptadores.salida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.inventario.ContenedoresCompartidos;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.LibroStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MensajesProcesados;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MovimientosStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ReservaRepository;
import com.roshka.challenge.inventario.dominio.EstadoReserva;
import com.roshka.challenge.inventario.dominio.LineaReserva;
import com.roshka.challenge.inventario.dominio.MotivoRechazo;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoDuplicado;
import com.roshka.challenge.inventario.dominio.Reserva;
import com.roshka.challenge.inventario.dominio.TipoAjuste;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DirtiesContext
@ImportTestcontainers(ContenedoresCompartidos.class)
class InventarioPersistenciaIT {

  @Autowired ProductoRepository productos;
  @Autowired ReservaRepository reservas;
  @Autowired LibroStock libro;
  @Autowired MovimientosStock movimientos;
  @Autowired MensajesProcesados mensajesProcesados;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate tx;

  @Test
  @DisplayName("REQ-13 · 001-AC-07 la base de datos rechaza stock disponible negativo")
  void debe_rechazar_la_actualizacion_cuando_disponible_quedaria_negativo() {
    var producto = almacenado(1);

    assertThatThrownBy(
            () ->
                jdbc.sql("update products set available = -1, reserved = 2 where id = :id")
                    .param("id", producto)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("REQ-10 · 001 RN-02 la base de datos rechaza stock que no se conserva")
  void debe_rechazar_la_actualizacion_cuando_el_stock_no_se_conserva() {
    var producto = almacenado(5);

    assertThatThrownBy(
            () ->
                jdbc.sql("update products set reserved = 1 where id = :id")
                    .param("id", producto)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("REQ-10 REQ-23 · 001-AC-02 los productos hacen ida y vuelta por el repositorio")
  void debe_encontrar_el_producto_cuando_se_inserta() {
    var producto = Producto.registrar(UUID.randomUUID(), "Keyboard " + UUID.randomUUID(), 7);
    productos.insertar(producto);

    assertThat(productos.buscarPorId(producto.id())).contains(producto);
    assertThat(productos.buscarPorId(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName(
      "REQ-22 · 011-AC-01 el repositorio encuentra un producto por nombre sin distinguir mayúsculas")
  void debe_encontrar_el_producto_por_nombre_sin_distinguir_mayusculas() {
    var producto = Producto.registrar(UUID.randomUUID(), "Mouse " + UUID.randomUUID(), 3);
    productos.insertar(producto);

    assertThat(productos.buscarPorNombre(producto.nombre().toUpperCase())).contains(producto);
    assertThat(productos.buscarPorNombre("no existe " + UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName(
      "REQ-16 · 011-AC-09 la base rechaza un nombre repetido aunque cambien mayúsculas y espacios")
  void debe_rechazar_el_nombre_repetido_a_nivel_de_base_de_datos() {
    var nombre = "Monitor " + UUID.randomUUID();
    productos.insertar(Producto.registrar(UUID.randomUUID(), nombre, 3));

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        insert into products (id, name, total, available, reserved, created_at)
                        values (:id, :name, 1, 1, 0, now())
                        """)
                    .param("id", UUID.randomUUID())
                    .param("name", "  " + nombre.toUpperCase() + " ")
                    .update())
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("products_name_unique");
  }

  @Test
  @DisplayName(
      "REQ-22 · 011-AC-05 el adaptador traduce la carrera por el índice en ProductoDuplicado")
  void debe_traducir_la_violacion_del_indice_en_producto_duplicado() {
    var nombre = "Teclado " + UUID.randomUUID();
    productos.insertar(Producto.registrar(UUID.randomUUID(), nombre, 3));

    assertThatThrownBy(() -> productos.insertar(Producto.registrar(UUID.randomUUID(), nombre, 1)))
        .isInstanceOf(ProductoDuplicado.class)
        .satisfies(e -> assertThat(((ProductoDuplicado) e).idExistente()).isNull());
  }

  @Test
  @DisplayName("REQ-11 · 001-AC-04 el libro reserva todas las líneas")
  void debe_mover_unidades_a_reservado_cuando_todas_las_lineas_caben() {
    var a = almacenado(5);
    var b = almacenado(2);

    var resultado = tx.execute(s -> libro.reservar(enOrdenDeBloqueo(linea(a, 3), linea(b, 2))));

    assertThat(resultado).isInstanceOf(LibroStock.Aplicado.class);
    assertThat(stockDe(a)).containsExactly(2, 3);
    assertThat(stockDe(b)).containsExactly(0, 2);
  }

  @Test
  @DisplayName("REQ-11 REQ-13 · 001-AC-05 el libro no aplica nada cuando una línea no cabe")
  void debe_dejar_intactos_todos_los_productos_cuando_una_linea_es_insuficiente() {
    var a = almacenado(5);
    var b = almacenado(1);

    var resultado = tx.execute(s -> libro.reservar(enOrdenDeBloqueo(linea(a, 3), linea(b, 2))));

    assertThat(resultado)
        .isEqualTo(
            new LibroStock.Rechazado(
                MotivoRechazo.INSUFFICIENT_STOCK,
                List.of(b),
                List.of(new LibroStock.Faltante(b, 2, 1))));
    assertThat(stockDe(a)).containsExactly(5, 0);
    assertThat(stockDe(b)).containsExactly(1, 0);
  }

  @Test
  @DisplayName("REQ-11 · 001-AC-06 el libro rechaza productos desconocidos sin tocar el stock")
  void debe_rechazar_como_desconocido_cuando_un_producto_no_existe() {
    var a = almacenado(5);
    var fantasma = UUID.randomUUID();

    var resultado =
        tx.execute(s -> libro.reservar(enOrdenDeBloqueo(linea(a, 1), linea(fantasma, 1))));

    assertThat(resultado)
        .isEqualTo(
            new LibroStock.Rechazado(
                MotivoRechazo.UNKNOWN_PRODUCT,
                List.of(fantasma),
                List.of(new LibroStock.Faltante(fantasma, 1, null))));
    assertThat(stockDe(a)).containsExactly(5, 0);
  }

  @Test
  @DisplayName("REQ-12 · 001-AC-08 el libro devuelve las unidades liberadas")
  void debe_devolver_unidades_a_disponible_cuando_se_libera() {
    var a = almacenado(5);
    tx.executeWithoutResult(s -> libro.reservar(List.of(linea(a, 3))));

    tx.executeWithoutResult(s -> libro.liberar(List.of(linea(a, 3))));

    assertThat(stockDe(a)).containsExactly(5, 0);
  }

  @Test
  @DisplayName(
      "REQ-15 · ADR 0010 las reservas hacen ida y vuelta y un segundo insert es clave duplicada")
  void debe_persistir_la_reserva_y_rechazar_un_segundo_insert_para_el_mismo_pedido() {
    var idPedido = UUID.randomUUID();
    var a = almacenado(5);
    var rechazado =
        Reserva.rechazado(
            idPedido, List.of(linea(a, 9)), MotivoRechazo.INSUFFICIENT_STOCK, List.of(a));

    tx.executeWithoutResult(s -> reservas.insertar(rechazado));

    var encontrado = tx.execute(s -> reservas.buscarParaActualizar(idPedido)).orElseThrow();
    assertThat(encontrado.estado()).isEqualTo(EstadoReserva.REJECTED);
    assertThat(encontrado.lineas()).containsExactly(linea(a, 9));
    assertThat(encontrado.motivoRechazo()).isEqualTo(MotivoRechazo.INSUFFICIENT_STOCK);
    assertThat(encontrado.idsProductoRechazados()).containsExactly(a);
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> reservas.insertar(rechazado)))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("REQ-12 · los cambios de estado de la reserva se persisten")
  void debe_persistir_el_estado_liberado_cuando_se_actualiza() {
    var idPedido = UUID.randomUUID();
    var reserva = Reserva.reservado(idPedido, List.of(linea(almacenado(5), 1)));
    tx.executeWithoutResult(s -> reservas.insertar(reserva));

    reserva.liberar();
    tx.executeWithoutResult(s -> reservas.actualizarEstado(reserva));

    assertThat(tx.execute(s -> reservas.buscarParaActualizar(idPedido)).orElseThrow().estado())
        .isEqualTo(EstadoReserva.RELEASED);
  }

  @Test
  @DisplayName("REQ-14 · 003 RN-02 un idMensaje solo se puede marcar como procesado una vez")
  void debe_informar_duplicado_cuando_el_mensaje_se_marca_dos_veces() {
    var idMensaje = UUID.randomUUID();

    Boolean primero = tx.execute(s -> mensajesProcesados.marcarProcesado(idMensaje));
    Boolean segundo = tx.execute(s -> mensajesProcesados.marcarProcesado(idMensaje));

    assertThat(primero).isTrue();
    assertThat(segundo).isFalse();
  }

  @Test
  @DisplayName(
      "EXT-03 · 014-AC-01/02 el repositorio guarda el stock nuevo de un producto bloqueado")
  void debe_guardar_el_stock_nuevo_y_conservar_lo_reservado() {
    var id = almacenado(5);
    tx.executeWithoutResult(s -> libro.reservar(List.of(linea(id, 3))));

    tx.executeWithoutResult(
        s -> {
          var actual = productos.bloquearPorId(id).orElseThrow();
          productos.actualizarStock(actual.conDisponible(10));
        });

    assertThat(stockDe(id)).containsExactly(10, 3);
    assertThat(totalDe(id)).isEqualTo(13);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-05 bloquear un producto desconocido no devuelve nada")
  void debe_devolver_vacio_cuando_se_bloquea_un_producto_desconocido() {
    Optional<Producto> bloqueado = tx.execute(s -> productos.bloquearPorId(UUID.randomUUID()));

    assertThat(bloqueado).isEmpty();
  }

  @Test
  @DisplayName("REQ-16 · 014-AC-04 la base rechaza un total por encima del máximo")
  void debe_rechazar_a_nivel_de_base_de_datos_un_total_mayor_al_maximo() {
    var id = almacenado(5);

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "update products set available = 10000001, total = 10000001 where id = :id")
                    .param("id", id)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("products_total_maximo");
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-10 los movimientos hacen ida y vuelta, más recientes primero")
  void debe_listar_los_movimientos_del_mas_reciente_al_mas_antiguo() {
    var id = almacenado(1);
    for (int i = 1; i <= 3; i++) {
      var cantidad = i;
      var momento = Instant.parse("2026-10-10T12:00:0%dZ".formatted(i));
      tx.executeWithoutResult(
          s ->
              movimientos.insertar(
                  new MovimientoStock(
                      UUID.randomUUID(),
                      id,
                      TipoAjuste.ADD,
                      cantidad,
                      "motivo " + cantidad,
                      1 + cantidad,
                      1 + cantidad,
                      momento)));
    }

    var pagina = movimientos.listar(id, new SolicitudPagina(0, 2));

    assertThat(pagina.items()).extracting(MovimientoStock::cantidad).containsExactly(3, 2);
    assertThat(pagina.items().getFirst().motivo()).isEqualTo("motivo 3");
    assertThat(pagina.items().getFirst().creadoEn())
        .isEqualTo(Instant.parse("2026-10-10T12:00:03Z"));
    assertThat(pagina.totalElementos()).isEqualTo(3);
    assertThat(pagina.totalPaginas()).isEqualTo(2);
    assertThat(movimientos.listar(UUID.randomUUID(), new SolicitudPagina(0, 2)).items()).isEmpty();
  }

  @Test
  @DisplayName(
      "EXT-03 · 014-AC-10 con la misma fecha el historial desempata por id, del mayor al menor")
  void debe_desempatar_por_id_descendente_cuando_dos_movimientos_tienen_la_misma_fecha() {
    var id = almacenado(1);
    var momento = Instant.parse("2026-10-10T12:00:00Z");
    var menor = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var mayor = UUID.fromString("00000000-0000-0000-0000-000000000002");
    for (var movimiento : List.of(menor, mayor)) {
      tx.executeWithoutResult(
          s ->
              movimientos.insertar(
                  new MovimientoStock(movimiento, id, TipoAjuste.ADD, 1, null, 2, 2, momento)));
    }

    var pagina = movimientos.listar(id, new SolicitudPagina(0, 10));

    assertThat(pagina.items()).extracting(MovimientoStock::id).containsExactly(mayor, menor);
  }

  @Test
  @DisplayName("EXT-03 · 014-AC-10 un movimiento exige un producto existente y datos válidos")
  void debe_rechazar_el_movimiento_cuando_el_producto_no_existe() {
    var huerfano =
        new MovimientoStock(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TipoAjuste.ADD,
            1,
            null,
            1,
            1,
            Instant.parse("2026-10-10T12:00:00Z"));

    assertThatThrownBy(() -> tx.executeWithoutResult(s -> movimientos.insertar(huerfano)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private int totalDe(UUID idProducto) {
    return jdbc.sql("select total from products where id = :id")
        .param("id", idProducto)
        .query(Integer.class)
        .single();
  }

  private UUID almacenado(int cantidad) {
    var producto = Producto.registrar(UUID.randomUUID(), "product-" + UUID.randomUUID(), cantidad);
    productos.insertar(producto);
    return producto.id();
  }

  private List<Integer> stockDe(UUID idProducto) {
    return jdbc.sql("select available, reserved from products where id = :id")
        .param("id", idProducto)
        .query((rs, n) -> List.of(rs.getInt("available"), rs.getInt("reserved")))
        .single();
  }

  private static LineaReserva linea(UUID idProducto, int cantidad) {
    return new LineaReserva(idProducto, cantidad);
  }

  private static List<LineaReserva> enOrdenDeBloqueo(LineaReserva... lineas) {
    return Reserva.enOrdenDeBloqueo(List.of(lineas));
  }
}
