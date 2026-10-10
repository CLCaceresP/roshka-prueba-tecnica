package com.roshka.challenge.gateway;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class GatewayConfiguration {

  @Bean
  ReactiveDiscoveryClient backendsEstaticos(GatewayProperties properties) {
    return new BackendsEstaticos(properties);
  }

  @Bean
  FiltrosEntrada.IdCorrelacion filtroIdCorrelacion() {
    return new FiltrosEntrada.IdCorrelacion();
  }

  @Bean
  RegistroSolicitudes registroSolicitudes() {
    return new RegistroSolicitudes();
  }

  @Bean
  FiltrosEntrada.PresenciaClaveIdempotencia filtroPresenciaClaveIdempotencia() {
    return new FiltrosEntrada.PresenciaClaveIdempotencia();
  }

  /**
   * Limita la tasa por dirección de cliente (redis-rate-limiter, token bucket compartido entre
   * réplicas).
   */
  @Bean
  KeyResolver resolutorClaveCliente() {
    return intercambio ->
        Mono.just(
            Optional.ofNullable(intercambio.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(direccion -> direccion.getHostAddress())
                .orElse("unknown"));
  }
}
