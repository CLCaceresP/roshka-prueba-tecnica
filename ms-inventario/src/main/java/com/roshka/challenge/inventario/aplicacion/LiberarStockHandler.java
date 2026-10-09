package com.roshka.challenge.inventario.aplicacion;

import static com.roshka.challenge.inventario.aplicacion.Respuestas.responderA;

import com.roshka.challenge.contrato.LiberarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.StockLiberado;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ManejarLiberarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ResultadoManejo;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.LibroStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MensajesProcesados;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.OutboxMensajes;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ReservaRepository;
import com.roshka.challenge.inventario.dominio.LineaReserva;
import com.roshka.challenge.inventario.dominio.Reserva;
import com.roshka.challenge.inventario.dominio.ResultadoLiberacion;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiberarStockHandler implements ManejarLiberarStock {

  private static final Logger log = LoggerFactory.getLogger(LiberarStockHandler.class);

  private final MensajesProcesados mensajesProcesados;
  private final ReservaRepository reservas;
  private final LibroStock libro;
  private final OutboxMensajes outbox;
  private final Clock reloj;

  public LiberarStockHandler(
      MensajesProcesados mensajesProcesados,
      ReservaRepository reservas,
      LibroStock libro,
      OutboxMensajes outbox,
      Clock reloj) {
    this.mensajesProcesados = mensajesProcesados;
    this.reservas = reservas;
    this.libro = libro;
    this.outbox = outbox;
    this.reloj = reloj;
  }

  @Override
  @Transactional
  public ResultadoManejo manejar(SobreMensaje<LiberarStock> comando) {
    var idPedido = comando.carga().idPedido();
    var pasos = Pasos.proceso(LiberarStockHandler.class, "Liberar stock").con("orderId", idPedido);
    return pasos.ejecutar(
        () -> {
          if (!pasos.medir(
              "Mensaje marcado como procesado",
              () -> mensajesProcesados.marcarProcesado(comando.idMensaje()))) {
            return ResultadoManejo.DUPLICADO;
          }
          var existente =
              pasos.medir(
                  "Reserva del pedido consultada", () -> reservas.buscarParaActualizar(idPedido));
          List<LineaReserva> lineas = existente.map(Reserva::lineas).orElse(List.of());
          var desenlace =
              existente.isPresent()
                  ? liberar(pasos, existente.get())
                  : pasos.medir(
                      "Tombstone registrado (no había nada reservado)",
                      () -> {
                        reservas.insertar(Reserva.tombstone(idPedido));
                        return ResultadoLiberacion.TOMBSTONE_REGISTRADO;
                      });
          registrarLiberacion(idPedido, desenlace, lineas);
          pasos.medir(
              "Respuesta registrada en el outbox",
              () ->
                  outbox.agregar(
                      Topicos.INVENTARIO_EVENTOS,
                      responderA(
                          comando,
                          TiposMensaje.STOCK_LIBERADO,
                          new StockLiberado(
                              idPedido,
                              com.roshka.challenge.contrato.ResultadoLiberacion.valueOf(
                                  desenlace.name())),
                          reloj)));
          return ResultadoManejo.APLICADO;
        });
  }

  private ResultadoLiberacion liberar(Pasos pasos, Reserva reserva) {
    var desenlace = reserva.liberar();
    if (desenlace == ResultadoLiberacion.DEVUELTO) {
      pasos.medir(
          "Stock devuelto a disponible en la base de datos", () -> libro.liberar(reserva.lineas()));
      pasos.medir("Estado de la reserva actualizado", () -> reservas.actualizarEstado(reserva));
    }
    return desenlace;
  }

  private static void registrarLiberacion(
      UUID idPedido, ResultadoLiberacion desenlace, List<LineaReserva> lineas) {
    var unidades = lineas.stream().mapToInt(LineaReserva::cantidad).sum();
    var items =
        lineas.stream()
            .map(linea -> linea.idProducto() + "×" + linea.cantidad())
            .collect(Collectors.joining(", "));
    var detalle =
        switch (desenlace) {
          case DEVUELTO -> "se devolvieron %d unidades (%s)".formatted(unidades, items);
          case TOMBSTONE_REGISTRADO ->
              "no había reserva: se registró un tombstone y se rechazará cualquier reserva posterior";
          case NADA_QUE_LIBERAR -> "la reserva ya estaba liberada o rechazada: no se devuelve nada";
        };
    AtributosLog.conPedido(log.atInfo(), idPedido)
        .addKeyValue("outcome", desenlace.name())
        .addKeyValue("units", unidades)
        .log("Stock del pedido %s liberado: %s".formatted(idPedido, detalle));
  }
}
