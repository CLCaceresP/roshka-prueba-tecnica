package com.roshka.challenge.pedidos.aplicacion.saga;

import com.roshka.challenge.pedidos.aplicacion.BloqueosPedido;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ExpirarSagasEstancadas;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoDistribuido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.BloqueoNoAdquirido;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

/**
 * Timeout de la saga (ADR 0013): pasa por el mismo bloqueo y la misma transacción que toda entrada
 * de la saga.
 */
@Service
public class TimeoutSagaHandler implements ExpirarSagasEstancadas {

  private static final Logger log = LoggerFactory.getLogger(TimeoutSagaHandler.class);

  private final PedidoRepository pedidos;
  private final BloqueoDistribuido bloqueo;
  private final PasosSaga pasos;
  private final ConfiguracionSaga ajustes;
  private final Clock reloj;

  public TimeoutSagaHandler(
      PedidoRepository pedidos,
      BloqueoDistribuido bloqueo,
      PasosSaga pasos,
      ConfiguracionSaga ajustes,
      Clock reloj) {
    this.pedidos = pedidos;
    this.bloqueo = bloqueo;
    this.pasos = pasos;
    this.ajustes = ajustes;
    this.reloj = reloj;
  }

  @Override
  public int expirarEstancadas() {
    var inicio = System.nanoTime();
    var umbral = reloj.instant().minus(ajustes.umbralTimeout());
    var candidatos = pedidos.buscarPendientesIniciadosAntes(umbral, ajustes.tamanioLoteTimeout());
    var expirado = 0;
    var ocupados = 0;
    for (var idPedido : candidatos) {
      try {
        if (bloqueo.llamarConBloqueo(
            BloqueosPedido.nombrePara(idPedido),
            ajustes.esperaBloqueoPedido(),
            ajustes.leaseBloqueoPedido(),
            () -> pasos.expirar(idPedido))) {
          expirado++;
        }
      } catch (BloqueoNoAdquirido | ConcurrencyFailureException ocupado) {
        // Una respuesta o una cancelación decidió este pedido de forma concurrente; un conflicto no
        // debe detener el lote, y la próxima ejecución vuelve a revisar el pedido.
        ocupados++;
        log.atInfo()
            .addKeyValue("orderId", idPedido.toString())
            .log(
                "Pedido %s omitido en la revisión de sagas estancadas: otra operación lo está decidiendo; se revisa en el próximo ciclo"
                    .formatted(idPedido));
      }
    }
    var ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    var evento =
        log.atLevel(candidatos.isEmpty() ? Level.DEBUG : Level.INFO)
            .addKeyValue("candidates", candidatos.size())
            .addKeyValue("expired", expirado)
            .addKeyValue("skipped", ocupados)
            .addKeyValue("durationMs", ms);
    evento.log(
        "Revisión de sagas estancadas: %d pedidos expirados de %d candidatos en %d ms (umbral %s)%s"
            .formatted(
                expirado,
                candidatos.size(),
                ms,
                ajustes.umbralTimeout(),
                ocupados == 0 ? "" : ", " + ocupados + " omitidos por estar ocupados"));
    return expirado;
  }
}
