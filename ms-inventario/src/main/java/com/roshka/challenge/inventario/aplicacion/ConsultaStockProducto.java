package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.inventario.aplicacion.puertos.entrada.ObtenerStockProducto;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoNoEncontrado;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsultaStockProducto implements ObtenerStockProducto {

  private static final Logger log = LoggerFactory.getLogger(ConsultaStockProducto.class);

  private final ProductoRepository productos;

  public ConsultaStockProducto(ProductoRepository productos) {
    this.productos = productos;
  }

  @Override
  public Producto stockDe(UUID idProducto) {
    var pasos = Pasos.proceso(ConsultaStockProducto.class, "Consultar stock");
    return pasos
        .con("productId", idProducto)
        .ejecutar(
            () -> {
              var producto =
                  pasos.medir(
                      "Stock consultado en la base de datos",
                      () ->
                          productos
                              .buscarPorId(idProducto)
                              .orElseThrow(() -> new ProductoNoEncontrado(idProducto)));
              log.atInfo()
                  .addKeyValue("productId", idProducto.toString())
                  .addKeyValue("available", producto.disponible())
                  .addKeyValue("reserved", producto.reservado())
                  .addKeyValue("total", producto.total())
                  .log(
                      "Producto %s: %d disponibles, %d reservados, %d en total"
                          .formatted(
                              idProducto,
                              producto.disponible(),
                              producto.reservado(),
                              producto.total()));
              return producto;
            });
  }
}
