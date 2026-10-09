package com.roshka.challenge.contrato;

public final class Topicos {

  /** Comandos de ms-pedidos hacia ms-inventario: ReservarStock, LiberarStock. */
  public static final String INVENTARIO_COMANDOS = "inventario.comandos";

  /** Respuestas de ms-inventario: StockReservado, StockRechazado, StockLiberado. */
  public static final String INVENTARIO_EVENTOS = "inventario.eventos";

  public static final String SUFIJO_MENSAJE_MUERTO = ".DLT";

  private Topicos() {}
}
