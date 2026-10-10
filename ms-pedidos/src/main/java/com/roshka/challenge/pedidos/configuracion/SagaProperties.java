package com.roshka.challenge.pedidos.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("saga")
record SagaProperties(TiempoAgotado timeout, BloqueoPedido bloqueoPedido) {

  record TiempoAgotado(Duration umbral, Duration intervaloVerificacion, int tamanioLote) {}

  record BloqueoPedido(Duration tiempoEspera, Duration lease) {}
}
