package com.roshka.challenge.inventario.aplicacion.puertos.salida;

import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import java.util.UUID;

/** Historial de ajustes de stock de un producto. */
public interface MovimientosStock {

  void insertar(MovimientoStock movimiento);

  /** Del más reciente al más antiguo y, a igualdad de fecha, por id descendente. */
  Pagina<MovimientoStock> listar(UUID idProducto, SolicitudPagina solicitud);
}
