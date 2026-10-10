package com.roshka.challenge.pedidos.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mensajeria")
record MensajeriaProperties(Topicos topicos) {

  record Topicos(int particiones, short replicas) {}
}
