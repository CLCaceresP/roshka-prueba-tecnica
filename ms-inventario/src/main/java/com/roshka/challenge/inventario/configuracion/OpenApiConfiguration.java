package com.roshka.challenge.inventario.configuracion;

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
  OpenAPI documentacionInventario(@Value("${info.app.version:0.1.0}") String version) {
    return new OpenAPI()
        .info(
            new Info()
                .title("API de inventario")
                .version(version)
                .description(
                    """
                    Permite administrar productos y su stock. El stock disponible nunca es \
                    negativo y disponible + reservado siempre es igual al total.

                    Las operaciones de escritura requieren el header Idempotency-Key. Los errores \
                    devuelven un cuerpo con el campo code."""))
        .addServersItem(new Server().url("/").description("Gateway"));
  }

  /** Los límites de paginación del Swagger salen de la configuración, no de la anotación. */
  @Bean
  OpenApiCustomizer limitesDePaginacion(PaginacionProperties paginacion) {
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
