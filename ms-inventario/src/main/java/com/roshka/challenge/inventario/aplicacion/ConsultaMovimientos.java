package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.inventario.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ListarMovimientos;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.MovimientosStock;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.dominio.MovimientoStock;
import com.roshka.challenge.inventario.dominio.ProductoNoEncontrado;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaMovimientos implements ListarMovimientos {

  private static final Logger log = LoggerFactory.getLogger(ConsultaMovimientos.class);

  private final ProductoRepository productos;
  private final MovimientosStock movimientos;
  private final ConfiguracionPaginacion paginacion;

  public ConsultaMovimientos(
      ProductoRepository productos,
      MovimientosStock movimientos,
      ConfiguracionPaginacion paginacion) {
    this.productos = productos;
    this.movimientos = movimientos;
    this.paginacion = paginacion;
  }

  @Override
  @Transactional(readOnly = true)
  public Pagina<MovimientoStock> listar(UUID idProducto, Integer numeroPagina, Integer tamanio) {
    var solicitud = SolicitudPagina.de(numeroPagina, tamanio, paginacion);
    var pasos =
        Pasos.proceso(ConsultaMovimientos.class, "Listar movimientos de stock")
            .con("productId", idProducto)
            .con("page", solicitud.pagina())
            .con("size", solicitud.tamanio());
    return pasos.ejecutar(
        () -> {
          pasos.medir(
              "Producto verificado",
              () ->
                  productos
                      .buscarPorId(idProducto)
                      .orElseThrow(() -> new ProductoNoEncontrado(idProducto)));
          var pagina =
              pasos.medir(
                  "Movimientos consultados en la base de datos",
                  () -> movimientos.listar(idProducto, solicitud));
          log.atInfo()
              .addKeyValue("returned", pagina.items().size())
              .addKeyValue("totalElements", pagina.totalElementos())
              .addKeyValue("totalPages", pagina.totalPaginas())
              .log(
                  "Movimientos del producto %s: página %d de %d (%d de %d movimientos)"
                      .formatted(
                          idProducto,
                          pagina.pagina(),
                          pagina.totalPaginas(),
                          pagina.items().size(),
                          pagina.totalElementos()));
          return pagina;
        });
  }
}
