package com.roshka.challenge.pedidos.adaptadores.salida.mensajeria;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Se enlaza y se registra desde la capa de configuración. */
@ConfigurationProperties("outbox.publicador")
public record OutboxProperties(
    Duration intervaloSondeo, int tamanioLote, Duration timeoutEnvio, Duration bloqueoMaximo) {}
