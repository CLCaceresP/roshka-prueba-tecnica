package com.roshka.challenge.pedidos.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bloqueo")
record BloqueoProperties(Redis redis) {

  record Redis(String direccion) {}
}
