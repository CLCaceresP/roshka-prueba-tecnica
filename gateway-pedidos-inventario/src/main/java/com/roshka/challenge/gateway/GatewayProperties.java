package com.roshka.challenge.gateway;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway")
record GatewayProperties(Map<String, List<URI>> servidoresBackend, LimiteTasa limiteTasa) {

  record LimiteTasa(int tasaReposicion, int capacidadRafaga) {}
}
