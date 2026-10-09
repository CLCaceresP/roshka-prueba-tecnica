package com.roshka.challenge.inventario.aplicacion.puertos.salida;

import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.dominio.Producto;
import java.util.Optional;
import java.util.UUID;

public interface ProductoRepository {

  void insertar(Producto producto);

  Optional<Producto> buscarPorId(UUID idProducto);

  /** Busca por nombre normalizado, sin distinguir mayúsculas ni minúsculas. */
  Optional<Producto> buscarPorNombre(String nombreNormalizado);

  /** Lee el producto y bloquea su fila hasta el final de la transacción. */
  Optional<Producto> bloquearPorId(UUID idProducto);

  /** Guarda {@code total}, {@code disponible} y {@code reservado} de un producto ya bloqueado. */
  void actualizarStock(Producto producto);

  /** Ordena por nombre normalizado sin distinguir mayúsculas y, a igualdad, por id. */
  Pagina<Producto> listar(SolicitudPagina solicitud);
}
