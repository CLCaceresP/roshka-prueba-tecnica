package com.roshka.challenge.pedidos.configuracion;

import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionConsulta;
import com.roshka.challenge.pedidos.aplicacion.consulta.ConfiguracionPaginacion;
import com.roshka.challenge.pedidos.aplicacion.idempotencia.ConfiguracionIdempotencia;
import com.roshka.challenge.pedidos.aplicacion.saga.ConfiguracionSaga;
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
  SagaProperties.class,
  PaginacionProperties.class,
  ConsultaProperties.class
})
class PedidosConfiguration {

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
        "idem:" + servicio + ":",
        properties.esperaBloqueoRecurso());
  }

  @Bean
  ConfiguracionSaga configuracionSaga(SagaProperties properties) {
    return new ConfiguracionSaga(
        properties.timeout().umbral(),
        properties.timeout().tamanioLote(),
        properties.bloqueoPedido().tiempoEspera(),
        properties.bloqueoPedido().lease());
  }

  @Bean
  ConfiguracionPaginacion configuracionPaginacion(PaginacionProperties properties) {
    return new ConfiguracionPaginacion(properties.tamanioPorDefecto(), properties.tamanioMaximo());
  }

  @Bean
  ConfiguracionConsulta configuracionConsulta(ConsultaProperties properties) {
    return new ConfiguracionConsulta(properties.zonaHoraria(), properties.rangoMaximoDias());
  }

  @Bean(destroyMethod = "shutdown")
  RedissonClient clienteRedisson(BloqueoProperties properties) {
    var config = new Config();
    config.setLazyInitialization(true);
    config.useSingleServer().setAddress(properties.redis().direccion());
    return Redisson.create(config);
  }
}
