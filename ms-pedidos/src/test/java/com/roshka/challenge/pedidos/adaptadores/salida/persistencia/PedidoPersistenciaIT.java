package com.roshka.challenge.pedidos.adaptadores.salida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.pedidos.ContenedoresCompartidos;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.EstadoPedido;
import com.roshka.challenge.pedidos.dominio.LineaPedido;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DirtiesContext
@ImportTestcontainers(ContenedoresCompartidos.class)
class PedidoPersistenciaIT {

  private static final Instant AHORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

  @Autowired PedidoRepository pedidos;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate tx;

  @Test
  @DisplayName("REQ-03 REQ-04 · 002-AC-04 los pedidos hacen ida y vuelta con sus líneas")
  void debe_encontrar_el_pedido_con_el_mismo_estado_cuando_se_inserta() {
    var pedido = colocado(AHORA);

    var encontrado = tx.execute(s -> pedidos.buscarPorId(pedido.id())).orElseThrow();

    assertThat(encontrado.estado()).isEqualTo(EstadoPedido.PENDING);
    assertThat(encontrado.lineas()).isEqualTo(pedido.lineas());
    assertThat(encontrado.idCorrelacion()).isEqualTo("corr-1");
    assertThat(encontrado.creadoEn()).isEqualTo(AHORA);
    assertThat(encontrado.sagaIniciadaEn()).isEqualTo(AHORA);
    Optional<Pedido> ausente = tx.execute(s -> pedidos.buscarPorId(UUID.randomUUID()));
    assertThat(ausente).isEmpty();
  }

  @Test
  @DisplayName("REQ-07 · ADR 0003 una actualización persiste el estado e incrementa la versión")
  void debe_persistir_el_estado_e_incrementar_la_version_cuando_se_actualiza() {
    var pedido = colocado(AHORA);

    tx.executeWithoutResult(
        s -> {
          var cargado = pedidos.buscarPorId(pedido.id()).orElseThrow();
          cargado.confirmarReserva(AHORA.plusSeconds(1));
          pedidos.actualizar(cargado);
        });

    assertThat(tx.execute(s -> pedidos.buscarPorId(pedido.id())).orElseThrow().estado())
        .isEqualTo(EstadoPedido.CONFIRMED);
    assertThat(versionDe(pedido.id())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "REQ-27 · ADR 0003 una compensación sin cambio de estado igual incrementa la versión")
  void debe_forzar_incremento_de_version_cuando_la_actualizacion_no_cambia_el_estado() {
    var pedido = colocado(AHORA);
    tx.executeWithoutResult(
        s -> {
          var cargado = pedidos.buscarPorId(pedido.id()).orElseThrow();
          cargado.cancelar(AHORA.plusSeconds(1));
          pedidos.actualizar(cargado);
        });

    tx.executeWithoutResult(
        s -> {
          var cargado = pedidos.buscarPorId(pedido.id()).orElseThrow();
          cargado.confirmarReserva(AHORA.plusSeconds(2));
          pedidos.actualizar(cargado);
        });

    assertThat(tx.execute(s -> pedidos.buscarPorId(pedido.id())).orElseThrow().estado())
        .isEqualTo(EstadoPedido.CANCELLED);
    assertThat(versionDe(pedido.id())).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "REQ-27 · ADR 0003 dos transacciones superpuestas sobre un pedido no pueden commitear ambas")
  void debe_rechazar_el_segundo_commit_cuando_el_pedido_cambio_concurrentemente() throws Exception {
    var pedido = colocado(AHORA);
    var cargado = new CountDownLatch(1);
    var continuar = new CountDownLatch(1);

    try (var ejecutor = Executors.newSingleThreadExecutor()) {
      var lento =
          ejecutor.submit(
              () ->
                  tx.executeWithoutResult(
                      s -> {
                        var obsoleto = pedidos.buscarPorId(pedido.id()).orElseThrow();
                        cargado.countDown();
                        esperar(continuar);
                        obsoleto.confirmarReserva(AHORA.plusSeconds(2));
                        pedidos.actualizar(obsoleto);
                      }));
      esperar(cargado);
      tx.executeWithoutResult(
          s -> {
            var nuevo = pedidos.buscarPorId(pedido.id()).orElseThrow();
            nuevo.cancelar(AHORA.plusSeconds(1));
            pedidos.actualizar(nuevo);
          });
      continuar.countDown();

      assertThatThrownBy(() -> lento.get(30, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
    assertThat(tx.execute(s -> pedidos.buscarPorId(pedido.id())).orElseThrow().estado())
        .isEqualTo(EstadoPedido.CANCELLED);
  }

  @Test
  @DisplayName("REQ-07 · 003-AC-10 solo los pedidos PENDING estancados son candidatos a timeout")
  void debe_encontrar_solo_pedidos_pendientes_iniciados_antes_del_umbral() {
    var estancado = colocado(AHORA.minusSeconds(120));
    var reciente = colocado(AHORA);
    var decidido = colocado(AHORA.minusSeconds(120));
    tx.executeWithoutResult(
        s -> {
          var cargado = pedidos.buscarPorId(decidido.id()).orElseThrow();
          cargado.confirmarReserva(AHORA);
          pedidos.actualizar(cargado);
        });

    var candidatos =
        tx.execute(s -> pedidos.buscarPendientesIniciadosAntes(AHORA.minusSeconds(60), 1000));

    assertThat(candidatos).contains(estancado.id()).doesNotContain(reciente.id(), decidido.id());
  }

  @Test
  @DisplayName("REQ-08 · 002 RN-07 la base de datos rechaza motivos de rechazo inconsistentes")
  void debe_rechazar_estado_rechazado_sin_motivo_a_nivel_de_base_de_datos() {
    var pedido = colocado(AHORA);

    assertThatThrownBy(
            () ->
                jdbc.sql("update orders set status = 'REJECTED' where id = :id")
                    .param("id", pedido.id())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Pedido colocado(Instant en) {
    var pedido =
        Pedido.colocar(
            UUID.randomUUID(), List.of(new LineaPedido(UUID.randomUUID(), 2)), "corr-1", en);
    tx.executeWithoutResult(s -> pedidos.insertar(pedido));
    return pedido;
  }

  private long versionDe(UUID idPedido) {
    return jdbc.sql("select version from orders where id = :id")
        .param("id", idPedido)
        .query(Long.class)
        .single();
  }

  private static void esperar(CountDownLatch cerrojo) {
    try {
      if (!cerrojo.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("el cerrojo agotó el tiempo");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
