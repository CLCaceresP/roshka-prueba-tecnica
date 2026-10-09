package com.roshka.challenge.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class PasosTest {

  private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(PasosTest.class);

  @BeforeEach
  void capturar() {
    eventos.start();
    logger.addAppender(eventos);
    logger.setLevel(Level.DEBUG);
  }

  @AfterEach
  void soltar() {
    logger.detachAppender(eventos);
    MDC.clear();
  }

  @Test
  @DisplayName("BON-03 · 010-AC-02 un paso registra su nombre, su duración y su resultado")
  void debe_registrar_el_paso_con_su_duracion_cuando_termina_bien() {
    var idPedido = UUID.randomUUID();
    var pasos = Pasos.proceso(PasosTest.class, "Colocar pedido").con("orderId", idPedido);

    var resultado = pasos.medir("Pedido guardado en la base de datos", () -> 42);

    assertThat(resultado).isEqualTo(42);
    assertThat(eventos.list)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.INFO);
              assertThat(e.getFormattedMessage())
                  .matches("Pedido guardado en la base de datos \\(\\d+ ms\\)");
              var datos = atributos(e);
              assertThat(datos)
                  .containsEntry("process", "Colocar pedido")
                  .containsEntry("step", "Pedido guardado en la base de datos")
                  .containsEntry("outcome", "ok")
                  .containsEntry("orderId", idPedido.toString())
                  .containsKey("durationMs");
            });
  }

  @Test
  @DisplayName("BON-03 · 010-AC-04 un paso que falla se registra en WARN y el error se propaga")
  void debe_registrar_en_warn_y_propagar_el_error_cuando_el_paso_falla() {
    var pasos = Pasos.proceso(PasosTest.class, "Colocar pedido");

    assertThatThrownBy(
            () ->
                pasos.medir(
                    "Pedido guardado en la base de datos",
                    () -> {
                      throw new IllegalStateException("base caída");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("base caída");

    assertThat(eventos.list)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage())
                  .matches("Pedido guardado en la base de datos falló tras \\d+ ms: base caída");
              assertThat(atributos(e))
                  .containsEntry("outcome", "error")
                  .containsEntry("error", "IllegalStateException");
            });
  }

  @Test
  @DisplayName("BON-03 · 010-AC-02 el proceso registra su total y cuántos pasos tuvo")
  void debe_registrar_el_total_del_proceso_con_el_numero_de_pasos_cuando_termina() {
    var pasos = Pasos.proceso(PasosTest.class, "Colocar pedido");

    var resultado =
        pasos.ejecutar(
            () -> {
              pasos.medir("Primero", () -> {});
              pasos.medir("Segundo", () -> "dos");
              return "listo";
            });

    assertThat(resultado).isEqualTo("listo");
    assertThat(eventos.list).hasSize(3);
    assertThat(eventos.list.getLast().getFormattedMessage())
        .matches("Colocar pedido completado en \\d+ ms \\(2 pasos\\)");
    assertThat(atributos(eventos.list.getLast()))
        .containsEntry("process", "Colocar pedido")
        .containsEntry("steps", "2")
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("BON-03 · 010-AC-04 un proceso que falla lo registra en WARN y propaga el error")
  void debe_registrar_en_warn_el_proceso_cuando_falla() {
    var pasos = Pasos.proceso(PasosTest.class, "Colocar pedido");

    assertThatThrownBy(
            () ->
                pasos.ejecutar(
                    () -> {
                      throw new IllegalArgumentException("pedido inválido");
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(eventos.list)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage())
                  .matches("Colocar pedido falló tras \\d+ ms: pedido inválido");
            });
  }

  @Test
  @DisplayName("BON-03 · 010 RN-07 no repite una clave que el MDC ya aporta")
  void debe_omitir_la_clave_cuando_el_mdc_ya_la_trae() {
    var idPedido = UUID.randomUUID();
    MDC.put("orderId", idPedido.toString());
    var pasos = Pasos.proceso(PasosTest.class, "Aplicar respuesta").con("orderId", idPedido);

    pasos.medir("Pedido cargado", () -> {});

    assertThat(atributos(eventos.list.getFirst())).doesNotContainKey("orderId");
  }

  private static Map<String, String> atributos(ILoggingEvent evento) {
    return evento.getKeyValuePairs().stream()
        .collect(Collectors.toMap(k -> k.key, k -> String.valueOf(k.value)));
  }
}
