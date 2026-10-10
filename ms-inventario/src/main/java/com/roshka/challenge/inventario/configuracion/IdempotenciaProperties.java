package com.roshka.challenge.inventario.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("idempotencia")
record IdempotenciaProperties(
    Duration ttl, Duration reintentarDespues, Duration esperaBloqueo, Duration leaseBloqueo) {}
