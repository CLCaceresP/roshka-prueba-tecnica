package com.roshka.challenge.inventario.aplicacion;

import com.roshka.challenge.inventario.aplicacion.puertos.entrada.RegistrarProducto;
import com.roshka.challenge.inventario.aplicacion.puertos.salida.ProductoRepository;
import com.roshka.challenge.inventario.dominio.Producto;
import com.roshka.challenge.inventario.dominio.ProductoDuplicado;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroProducto implements RegistrarProducto {

  private static final Logger log = LoggerFactory.getLogger(RegistroProducto.class);

  private final ProductoRepository productos;

  public RegistroProducto(ProductoRepository productos) {
    this.productos = productos;
  }

  @Override
  @Transactional
  public Producto registrar(String nombre, int cantidadInicial) {
    var pasos = Pasos.proceso(RegistroProducto.class, "Registrar producto");
    return pasos.ejecutar(
        () -> {
          var producto =
              pasos.medir(
                  "Producto creado y validado",
                  () -> Producto.registrar(UUID.randomUUID(), nombre, cantidadInicial));
          pasos.con("productId", producto.id());
          pasos.medir(
              "Nombre verificado (sin duplicados)",
              () ->
                  productos
                      .buscarPorNombre(producto.nombre())
                      .ifPresent(
                          existente -> {
                            throw new ProductoDuplicado(producto.nombre(), existente.id());
                          }));
          pasos.medir("Producto guardado en la base de datos", () -> productos.insertar(producto));
          log.atInfo()
              .addKeyValue("productId", producto.id().toString())
              .addKeyValue("name", producto.nombre())
              .addKeyValue("initialQuantity", cantidadInicial)
              .log(
                  "Producto %s registrado: \"%s\" con %d unidades disponibles"
                      .formatted(producto.id(), producto.nombre(), cantidadInicial));
          return producto;
        });
  }
}
