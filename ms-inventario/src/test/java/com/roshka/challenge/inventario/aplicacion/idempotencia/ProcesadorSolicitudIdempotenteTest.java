package com.roshka.challenge.inventario.aplicacion.idempotencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roshka.challenge.inventario.aplicacion.puertos.salida.AlmacenClavesIdempotencia;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.BloqueoDistribuido;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.BloqueoNoAdquirido;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcesadorSolicitudIdempotenteTest {

  private static final Duration TTL = Duration.ofHours(24);
  private static final Duration REINTENTAR_DESPUES = Duration.ofSeconds(1);

  private final Clock reloj = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
  private final AlmacenClavesEnMemoria almacen = new AlmacenClavesEnMemoria();
  private final BloqueoConmutable bloqueo = new BloqueoConmutable();
  private final ConfiguracionIdempotencia ajustes =
      new ConfiguracionIdempotencia(
          TTL, REINTENTAR_DESPUES, Duration.ZERO, Duration.ofSeconds(10), "idem:t:");
  private final ProcesadorSolicitudIdempotente procesador =
      new ProcesadorSolicitudIdempotente(
          bloqueo, almacen, new TransaccionIdempotente(almacen, ajustes, reloj), ajustes, reloj);

  private final AtomicInteger ejecuciones = new AtomicInteger();
  private final RespuestaAlmacenada creado =
      new RespuestaAlmacenada(201, Map.of("Location", "/products/1"), "{\"productId\":\"1\"}");

  @Test
  @DisplayName("REQ-24 · 005-AC-02 la primera solicitud se ejecuta y se registra su respuesta")
  void debe_ejecutar_la_accion_y_registrar_la_respuesta_cuando_la_clave_es_nueva() {
    var resultado = procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion());

    assertThat(resultado).isEqualTo(new RespuestaIdempotente(creado, false));
    assertThat(ejecuciones).hasValue(1);
    assertThat(almacen.claves.get("k-1").respuestaHttp()).isEqualTo(creado);
  }

  @Test
  @DisplayName("REQ-24 · 005-AC-02 un duplicado reproduce la respuesta registrada sin ejecutarse")
  void
      debe_reproducir_la_respuesta_registrada_cuando_llegan_de_nuevo_la_misma_clave_y_la_misma_solicitud() {
    procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion());

    var reproducir = procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion());

    assertThat(reproducir).isEqualTo(new RespuestaIdempotente(creado, true));
    assertThat(ejecuciones).hasValue(1);
  }

  @Test
  @DisplayName("REQ-24 · 005-AC-03 la misma clave con una solicitud distinta se rechaza")
  void debe_rechazar_la_solicitud_cuando_la_clave_se_uso_para_otra_solicitud() {
    procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion());

    assertThatThrownBy(
            () -> procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-b"), accion()))
        .isInstanceOf(ClaveIdempotenciaReutilizada.class);
    assertThat(ejecuciones).hasValue(1);
  }

  @Test
  @DisplayName("REQ-24 REQ-25 · 005-AC-05 una clave en proceso en otro lado responde «en proceso»")
  void debe_informar_en_proceso_con_retry_after_cuando_el_bloqueo_lo_retiene_otro() {
    bloqueo.retenido = true;

    assertThatThrownBy(
            () -> procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion()))
        .isInstanceOfSatisfying(
            ClaveIdempotenciaEnProceso.class,
            e -> assertThat(e.reintentarDespues()).isEqualTo(REINTENTAR_DESPUES));
    assertThat(bloqueo.ultimoNombre).isEqualTo("idem:t:k-1");
    assertThat(ejecuciones).hasValue(0);
  }

  @Test
  @DisplayName("REQ-24 · 005-AC-08 las solicitudes fallidas no se registran y se pueden reintentar")
  void debe_no_registrar_la_clave_cuando_la_accion_falla() {
    assertThatThrownBy(
            () ->
                procesador.ejecutar(
                    new SolicitudIdempotente("k-1", "hash-a"),
                    () -> {
                      throw new IllegalArgumentException("inválido");
                    }))
        .isInstanceOf(IllegalArgumentException.class);
    almacen.rollback();

    var reintentar = procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion());

    assertThat(reintentar.reproducido()).isFalse();
    assertThat(ejecuciones).hasValue(1);
  }

  @Test
  @DisplayName(
      "REQ-24 · 005-AC-07 perder el reclamo ante una solicitud concurrente reproduce su respuesta")
  void
      debe_reproducir_la_respuesta_ganadora_cuando_el_reclamo_se_pierde_ante_una_solicitud_concurrente() {
    almacen.ganadorConcurrente = new AlmacenClavesIdempotencia.ClaveRegistrada("hash-a", creado);

    var resultado = procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion());

    assertThat(resultado).isEqualTo(new RespuestaIdempotente(creado, true));
    assertThat(ejecuciones).hasValue(0);
  }

  @Test
  @DisplayName("REQ-24 · 005-AC-09 una clave vencida se trata como nueva")
  void debe_ejecutar_de_nuevo_cuando_la_clave_registrada_expiro() {
    almacen.claves.put(
        "k-1", new Almacenado("hash-old", creado, reloj.instant().minus(Duration.ofMinutes(1))));

    var resultado = procesador.ejecutar(new SolicitudIdempotente("k-1", "hash-a"), accion());

    assertThat(resultado.reproducido()).isFalse();
    assertThat(ejecuciones).hasValue(1);
    assertThat(almacen.claves.get("k-1").expiraEn()).isEqualTo(reloj.instant().plus(TTL));
  }

  private Supplier<RespuestaAlmacenada> accion() {
    return () -> {
      ejecuciones.incrementAndGet();
      return creado;
    };
  }

  private record Almacenado(String huella, RespuestaAlmacenada respuestaHttp, Instant expiraEn) {}

  /**
   * Imita la tabla: las filas de una transacción fallida desaparecen al llamar a {@link #rollback}.
   */
  private static final class AlmacenClavesEnMemoria implements AlmacenClavesIdempotencia {

    final Map<String, Almacenado> claves = new HashMap<>();
    AlmacenClavesIdempotencia.ClaveRegistrada ganadorConcurrente;
    private String sinCommit;

    @Override
    public Optional<ClaveRegistrada> buscarValido(String clave, Instant ahora) {
      return Optional.ofNullable(claves.get(clave))
          .filter(
              almacenado ->
                  almacenado.respuestaHttp() != null && almacenado.expiraEn().isAfter(ahora))
          .map(almacenado -> new ClaveRegistrada(almacenado.huella(), almacenado.respuestaHttp()));
    }

    @Override
    public boolean reclamar(String clave, String huella, Instant ahora, Instant expiraEn) {
      if (ganadorConcurrente != null) {
        claves.put(
            clave,
            new Almacenado(
                ganadorConcurrente.huella(), ganadorConcurrente.respuestaHttp(), expiraEn));
        return false;
      }
      var actual = claves.get(clave);
      if (actual != null && actual.expiraEn().isAfter(ahora)) {
        return false;
      }
      claves.put(clave, new Almacenado(huella, null, expiraEn));
      sinCommit = clave;
      return true;
    }

    @Override
    public void completar(String clave, RespuestaAlmacenada respuestaHttp) {
      var reclamado = claves.get(clave);
      claves.put(clave, new Almacenado(reclamado.huella(), respuestaHttp, reclamado.expiraEn()));
      sinCommit = null;
    }

    void rollback() {
      if (sinCommit != null) {
        claves.remove(sinCommit);
        sinCommit = null;
      }
    }
  }

  private static final class BloqueoConmutable implements BloqueoDistribuido {

    boolean retenido;
    String ultimoNombre;

    @Override
    public <T> T llamarConBloqueo(
        String nombre, Duration espera, Duration lease, Supplier<T> accion) {
      ultimoNombre = nombre;
      if (retenido) {
        throw new BloqueoNoAdquirido(nombre);
      }
      return accion.get();
    }
  }
}
