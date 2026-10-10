package com.roshka.challenge.inventario.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reintentos bloqueantes; tiempoMaximoTranscurrido debe quedar por debajo de max.poll.interval.ms
 * (ADR 0006).
 */
@ConfigurationProperties("mensajeria.reintentos")
record ReintentosProperties(
    Duration intervaloInicial,
    double multiplicador,
    Duration intervaloMaximo,
    Duration tiempoMaximoTranscurrido) {}
