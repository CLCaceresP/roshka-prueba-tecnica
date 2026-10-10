package com.roshka.challenge.pedidos.aplicacion;

import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionConsulta;
import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.pedidos.aplicacion.consulta.Pagina;
import com.roshka.challenge.pedidos.aplicacion.consulta.RangoFechas;
import com.roshka.challenge.pedidos.aplicacion.consulta.SolicitudPagina;
import com.roshka.challenge.pedidos.aplicacion.puertos.entrada.ListarPedidos;
import com.roshka.challenge.pedidos.aplicacion.puertos.salida.PedidoRepository;
import com.roshka.challenge.pedidos.dominio.Pedido;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaPedidos implements ListarPedidos {

  private static final Logger log = LoggerFactory.getLogger(ConsultaPedidos.class);

  private final PedidoRepository pedidos;
  private final ConfiguracionConsulta consulta;
  private final ConfiguracionPaginacion paginacion;
  private final Clock reloj;

  public ConsultaPedidos(
      PedidoRepository pedidos,
      ConfiguracionConsulta consulta,
      ConfiguracionPaginacion paginacion,
      Clock reloj) {
    this.pedidos = pedidos;
    this.consulta = consulta;
    this.paginacion = paginacion;
    this.reloj = reloj;
  }

  @Override
  @Transactional(readOnly = true)
  public Pagina<Pedido> listar(LocalDate desde, LocalDate hasta, Integer pagina, Integer tamanio) {
    var rango = RangoFechas.de(desde, hasta, consulta, reloj);
    var solicitud = SolicitudPagina.de(pagina, tamanio, paginacion);
    var pasos =
        Pasos.proceso(ConsultaPedidos.class, "Listar pedidos")
            .con("range", rango.descripcion())
            .con("page", solicitud.pagina())
            .con("size", solicitud.tamanio());
    return pasos.ejecutar(
        () -> {
          var resultado =
              pasos.medir(
                  "Pedidos consultados en la base de datos",
                  () ->
                      pedidos.buscarPorCreacion(
                          rango.instanteDesde(), rango.instanteHasta(), solicitud));
          log.atInfo()
              .addKeyValue("returned", resultado.items().size())
              .addKeyValue("totalElements", resultado.totalElementos())
              .addKeyValue("totalPages", resultado.totalPaginas())
              .log(
                  "Listado de pedidos %s: página %d de %d (%d de %d pedidos)"
                      .formatted(
                          rango.descripcion(),
                          resultado.pagina(),
                          resultado.totalPaginas(),
                          resultado.items().size(),
                          resultado.totalElementos()));
          return resultado;
        });
  }
}
