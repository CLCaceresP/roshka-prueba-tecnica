package com.roshka.challenge.pedidos.aplicacion.puertos.entrada;

import com.roshka.challenge.contrato.SobreMensaje;
import com.roshka.challenge.contrato.StockLiberado;
import com.roshka.challenge.contrato.StockRechazado;
import com.roshka.challenge.contrato.StockReservado;

/**
 * Punto de entrada del orquestador de la saga para las respuestas de inventario. Cada respuesta se
 * aplica bajo el bloqueo del pedido, en una transacción que también registra el idMensaje y
 * cualquier compensación (spec 004).
 */
public interface ManejarRespuestaInventario {

  ResultadoManejo alStockReservado(SobreMensaje<StockReservado> evento);

  ResultadoManejo alStockRechazado(SobreMensaje<StockRechazado> evento);

  ResultadoManejo alStockLiberado(SobreMensaje<StockLiberado> evento);
}
