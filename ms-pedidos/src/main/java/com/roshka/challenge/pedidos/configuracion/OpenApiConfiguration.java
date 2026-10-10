package com.roshka.challenge.pedidos.configuracion;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

  @Bean
  OpenAPI documentacionPedidos(@Value("${info.app.version:0.1.0}") String version) {
    return new OpenAPI()
        .info(
            new Info()
                .title("API de pedidos")
                .version(version)
                .description(
                    """
                    Permite colocar, consultar y cancelar pedidos. Cada pedido nace en estado \
                    PENDING y pasa a CONFIRMED o REJECTED cuando inventario responde sobre el stock.

                    Las operaciones de escritura requieren el header Idempotency-Key. Los errores \
                    devuelven un cuerpo con el campo code."""))
        .addServersItem(new Server().url("/").description("Gateway"));
  }

  /** Los límites de paginación del Swagger salen de la configuración, no de la anotación. */
  @Bean
  OpenApiCustomizer limitesConfigurados(
      PaginacionProperties paginacion, ConsultaProperties consulta) {
    return openApi ->
        openApi
            .getPaths()
            .values()
            .forEach(
                ruta ->
                    ruta.readOperations()
                        .forEach(
                            operacion -> {
                              if (operacion.getParameters() == null) {
                                return;
                              }
                              for (var parametro : operacion.getParameters()) {
                                if ("from".equals(parametro.getName())) {
                                  parametro.setDescription(
                                      "Fecha inicial en formato MM/dd/yyyy. Si no se envía ninguna fecha se usa hoy. "
                                          + "El rango no puede superar los "
                                          + consulta.rangoMaximoDias()
                                          + " días.");
                                }
                                if ("to".equals(parametro.getName())) {
                                  parametro.setDescription(
                                      "Fecha final inclusive en formato MM/dd/yyyy. Si solo se envía una de las dos, "
                                          + "el rango es ese día. Máximo "
                                          + consulta.rangoMaximoDias()
                                          + " días.");
                                }
                                if ("size".equals(parametro.getName())) {
                                  parametro.setDescription(
                                      "Elementos por página, de 1 a "
                                          + paginacion.tamanioMaximo()
                                          + ".");
                                  parametro.getSchema().setDefault(paginacion.tamanioPorDefecto());
                                  parametro
                                      .getSchema()
                                      .setMaximum(BigDecimal.valueOf(paginacion.tamanioMaximo()));
                                }
                              }
                            }));
  }
}
