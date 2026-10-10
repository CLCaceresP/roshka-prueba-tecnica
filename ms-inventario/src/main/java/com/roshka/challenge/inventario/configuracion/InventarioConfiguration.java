package com.roshka.challenge.inventario.configuracion;

import com.roshka.challenge.inventario.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.inventario.aplicacion.idempotencia.ConfiguracionIdempotencia;
import java.time.Clock;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  IdempotenciaProperties.class,
  BloqueoProperties.class,
  PaginacionProperties.class
})
class InventarioConfiguration {

  @Bean
  Clock reloj() {
    return Clock.systemUTC();
  }

  @Bean
  ConfiguracionIdempotencia configuracionIdempotencia(
      IdempotenciaProperties properties, @Value("${spring.application.name}") String servicio) {
    return new ConfiguracionIdempotencia(
        properties.ttl(),
        properties.reintentarDespues(),
        properties.esperaBloqueo(),
        properties.leaseBloqueo(),
        "idem:" + servicio + ":");
  }

  @Bean
  ConfiguracionPaginacion configuracionPaginacion(PaginacionProperties properties) {
    return new ConfiguracionPaginacion(properties.tamanioPorDefecto(), properties.tamanioMaximo());
  }

  @Bean(destroyMethod = "shutdown")
  RedissonClient clienteRedisson(BloqueoProperties properties) {
    var config = new Config();
    config.setLazyInitialization(true);
    config.useSingleServer().setAddress(properties.redis().direccion());
    return Redisson.create(config);
  }
}
