package com.roshka.challenge.inventario.aplicacion;

import static com.roshka.challenge.inventario.aplicacion.Respuestas.responderA;

import com.roshka.challenge.contrato.LineaStock;
import com.roshka.challenge.contrato.MotivoRechazoStock;
import com.roshka.challenge.contrato.ReservarStock;
import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.StockRechazado;
import com.roshka.challenge.contrato.StockReservado;
import com.roshka.challenge.contrato.TiposMensaje;
import com.roshka.challenge.contrato.Topicos;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ManejarReservarStock;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ResultadoManejo;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.LibroStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MensajesProcesados;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MetricasInventario;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.OutboxMensajes;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ReservaRepository;
import com.roshka.challenge.inventario.dominio.LineaReserva;
import com.roshka.challenge.inventario.dominio.MotivoRechazo;
import com.roshka.challenge.inventario.dominio.Reserva;
import com.roshka.challenge.inventario.dominio.RespuestaReserva;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservarStockHandler implements ManejarReservarStock {

  private static final Logger log = LoggerFactory.getLogger(ReservarStockHandler.class);

  private final MensajesProcesados mensajesProcesados;
  private final ReservaRepository reservas;
  private final LibroStock libro;
  private final OutboxMensajes outbox;
  private final MetricasInventario metricas;
  private final Clock reloj;

  public ReservarStockHandler(
      MensajesProcesados mensajesProcesados,
      ReservaRepository reservas,
      LibroStock libro,
      OutboxMensajes outbox,
      MetricasInventario metricas,
      Clock reloj) {
    this.mensajesProcesados = mensajesProcesados;
    this.reservas = reservas;
    this.libro = libro;
    this.outbox = outbox;
    this.metricas = metricas;
    this.reloj = reloj;
  }

  @Override
  @Transactional
  public ResultadoManejo manejar(SobreMensaje<ReservarStock> comando) {
    var idPedido = comando.carga().idPedido();
    var pasos =
        Pasos.proceso(ReservarStockHandler.class, "Reservar stock").con("orderId", idPedido);
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
          var reserva =
              existente.isPresent()
                  ? registrarRepetida(idPedido, existente.get())
                  : reservarNueva(pasos, idPedido, comando.carga().lineas());
          pasos.medir(
              "Respuesta registrada en el outbox",
              () ->
                  outbox.agregar(
                      Topicos.INVENTARIO_EVENTOS,
                      respuesta(comando, reserva.respuestaAReservar())));
          return ResultadoManejo.APLICADO;
        });
  }

  private Reserva registrarRepetida(UUID idPedido, Reserva reserva) {
    var evento =
        AtributosLog.conPedido(log.atInfo(), idPedido)
            .addKeyValue("result", reserva.estado().name());
    var motivo = "";
    if (reserva.motivoRechazo() != null) {
      evento = evento.addKeyValue("reason", reserva.motivoRechazo().name());
      motivo = ", " + reserva.motivoRechazo();
    }
    evento.log(
        "Reserva del pedido %s ya estaba decidida (%s%s): se responde de nuevo"
            .formatted(idPedido, reserva.estado(), motivo));
    return reserva;
  }

  private Reserva reservarNueva(Pasos pasos, UUID idPedido, List<LineaStock> solicitado) {
    var lineas =
        Reserva.enOrdenDeBloqueo(
            solicitado.stream()
                .map(linea -> new LineaReserva(linea.idProducto(), linea.cantidad()))
                .toList());
    var resultado =
        pasos.medir(
            "Disponibilidad verificada y stock descontado en la base de datos",
            () -> libro.reservar(lineas));
    var reserva =
        switch (resultado) {
          case LibroStock.Aplicado aplicado -> Reserva.reservado(idPedido, lineas);
          case LibroStock.Rechazado rechazado ->
              Reserva.rechazado(idPedido, lineas, rechazado.motivo(), rechazado.idsProducto());
        };
    registrarDecision(idPedido, reserva, resultado);
    pasos.medir("Reserva guardada", () -> reservas.insertar(reserva));
    metricas.reservaDecidida(reserva.motivoRechazo());
    return reserva;
  }

  private static void registrarDecision(
      UUID idPedido, Reserva reserva, LibroStock.ResultadoLibro resultado) {
    var evento =
        AtributosLog.conPedido(log.atInfo(), idPedido)
            .addKeyValue("result", reserva.estado().name());
    if (resultado instanceof LibroStock.Rechazado rechazado) {
      var primero = rechazado.faltantes().getFirst();
      evento
          .addKeyValue("reason", rechazado.motivo().name())
          .addKeyValue("productId", primero.idProducto().toString())
          .addKeyValue("requested", primero.solicitado())
          .addKeyValue("available", String.valueOf(primero.disponible()))
          .log(
              "Reserva del pedido %s rechazada%s"
                  .formatted(
                      idPedido, detalleDelRechazo(rechazado.motivo(), rechazado.faltantes())));
      return;
    }
    var unidades = reserva.lineas().stream().mapToInt(LineaReserva::cantidad).sum();
    var items =
        reserva.lineas().stream()
            .map(linea -> linea.idProducto() + "×" + linea.cantidad())
            .collect(Collectors.joining(", "));
    evento
        .addKeyValue("lines", reserva.lineas().size())
        .addKeyValue("units", unidades)
        .addKeyValue("items", items)
        .log(
            "Reserva del pedido %s aprobada (%d líneas, %d unidades: %s)"
                .formatted(idPedido, reserva.lineas().size(), unidades, items));
  }

  private static String detalleDelRechazo(
      MotivoRechazo motivo, List<LibroStock.Faltante> faltantes) {
    return switch (motivo) {
      case INSUFFICIENT_STOCK ->
          " por stock insuficiente: "
              + faltantes.stream()
                  .map(
                      f ->
                          "producto %s pedía %d y había %s disponibles"
                              .formatted(f.idProducto(), f.solicitado(), f.disponible()))
                  .collect(Collectors.joining("; "));
      case UNKNOWN_PRODUCT ->
          ": "
              + faltantes.stream()
                  .map(f -> "el producto %s no existe".formatted(f.idProducto()))
                  .collect(Collectors.joining("; "));
      case RESERVATION_RELEASED -> ": el pedido ya había sido liberado";
    };
  }

  private SobreMensaje<?> respuesta(
      SobreMensaje<ReservarStock> comando, RespuestaReserva contestacion) {
    var idPedido = comando.carga().idPedido();
    return switch (contestacion) {
      case RespuestaReserva.Confirmado confirmado ->
          responderA(
              comando,
              TiposMensaje.STOCK_RESERVADO,
              new StockReservado(idPedido, aContrato(confirmado.lineas())),
              reloj);
      case RespuestaReserva.Rechazado rechazado ->
          responderA(
              comando,
              TiposMensaje.STOCK_RECHAZADO,
              new StockRechazado(idPedido, aContrato(rechazado.motivo()), rechazado.idsProducto()),
              reloj);
    };
  }

  private static MotivoRechazoStock aContrato(MotivoRechazo motivo) {
    return switch (motivo) {
      case INSUFFICIENT_STOCK -> MotivoRechazoStock.STOCK_INSUFICIENTE;
      case UNKNOWN_PRODUCT -> MotivoRechazoStock.PRODUCTO_DESCONOCIDO;
      case RESERVATION_RELEASED -> MotivoRechazoStock.RESERVA_LIBERADA;
    };
  }

  private static List<LineaStock> aContrato(List<LineaReserva> lineas) {
    return lineas.stream()
        .map(linea -> new LineaStock(linea.idProducto(), linea.cantidad()))
        .toList();
  }
}
