package com.roshka.challenge.inventario.aplicacion;

import java.util.UUID;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Agrega el pedido a un log sin repetir una clave que el MDC ya aporta: dentro de un listener el
 * MDC trae {@code orderId}, y la consola JSON falla si la misma clave se escribe dos veces.
 */
public final class AtributosLog {

  private static final String ID_PEDIDO = "orderId";

  private AtributosLog() {}

  public static LoggingEventBuilder conPedido(LoggingEventBuilder evento, UUID idPedido) {
    var valor = idPedido.toString();
    return valor.equals(MDC.get(ID_PEDIDO)) ? evento : evento.addKeyValue(ID_PEDIDO, valor);
  }
}
