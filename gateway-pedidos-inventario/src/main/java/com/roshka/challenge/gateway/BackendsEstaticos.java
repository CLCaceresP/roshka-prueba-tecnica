package com.roshka.challenge.gateway;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import reactor.core.publisher.Flux;

/** Réplicas fijas de cada servicio tomadas de la configuración; el balanceador rota entre ellas. */
class BackendsEstaticos implements ReactiveDiscoveryClient {

  private final GatewayProperties properties;

  BackendsEstaticos(GatewayProperties properties) {
    this.properties = properties;
  }

  @Override
  public String description() {
    return "Static backends from gateway.servidores-backend";
  }

  @Override
  public Flux<ServiceInstance> getInstances(String idServicio) {
    var uris = properties.servidoresBackend().getOrDefault(idServicio, List.of());
    return Flux.fromStream(
        IntStream.range(0, uris.size())
            .mapToObj(
                i ->
                    new DefaultServiceInstance(
                        idServicio + "-" + (i + 1),
                        idServicio,
                        uris.get(i).getHost(),
                        uris.get(i).getPort(),
                        false)));
  }

  @Override
  public Flux<String> getServices() {
    return Flux.fromIterable(properties.servidoresBackend().keySet());
  }
}
