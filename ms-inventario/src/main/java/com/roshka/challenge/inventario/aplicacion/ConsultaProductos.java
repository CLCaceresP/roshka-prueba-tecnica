package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.inventario.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.inventario.aplicacion.consulta.Pagina;
import com.roshka.challenge.inventario.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ListarProductos;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.dominio.Producto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaProductos implements ListarProductos {

  private static final Logger log = LoggerFactory.getLogger(ConsultaProductos.class);

  private final ProductoRepository productos;
  private final ConfiguracionPaginacion paginacion;

  public ConsultaProductos(ProductoRepository productos, ConfiguracionPaginacion paginacion) {
    this.productos = productos;
    this.paginacion = paginacion;
  }

  @Override
  @Transactional(readOnly = true)
  public Pagina<Producto> listar(Integer numeroPagina, Integer tamanio) {
    var solicitud = SolicitudPagina.de(numeroPagina, tamanio, paginacion);
    var pasos =
        Pasos.proceso(ConsultaProductos.class, "Listar productos")
            .con("page", solicitud.pagina())
            .con("size", solicitud.tamanio());
    return pasos.ejecutar(
        () -> {
          var pagina =
              pasos.medir(
                  "Productos consultados en la base de datos", () -> productos.listar(solicitud));
          log.atInfo()
              .addKeyValue("returned", pagina.items().size())
              .addKeyValue("totalElements", pagina.totalElementos())
              .addKeyValue("totalPages", pagina.totalPaginas())
              .log(
                  "Listado de productos: página %d de %d (%d de %d productos)"
                      .formatted(
                          pagina.pagina(),
                          pagina.totalPaginas(),
                          pagina.items().size(),
                          pagina.totalElementos()));
          return pagina;
        });
  }
}
