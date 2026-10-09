package com.roshka.challenge.contrato;

public final class TiposMensaje {

  public static final int VERSION_ESQUEMA = 1;

  public static final String RESERVAR_STOCK = "ReservarStock";
  public static final String LIBERAR_STOCK = "LiberarStock";
  public static final String STOCK_RESERVADO = "StockReservado";
  public static final String STOCK_RECHAZADO = "StockRechazado";
  public static final String STOCK_LIBERADO = "StockLiberado";

  private TiposMensaje() {}
}
