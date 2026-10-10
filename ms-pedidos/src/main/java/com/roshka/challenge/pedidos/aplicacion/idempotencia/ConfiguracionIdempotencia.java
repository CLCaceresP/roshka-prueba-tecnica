package com.roshka.challenge.pedidos.aplicacion.idempotencia;

import java.time.Duration;

/**
 * @param prefijoBloqueo prefijo del nombre del bloqueo distribuido, p. ej. {@code idem:ms-pedidos:}
 * @param esperaBloqueo cuánto espera un duplicado el bloqueo de la clave antes de recibir 409; cero
 *     responde de inmediato
 * @param esperaBloqueoRecurso cuánto espera una solicitud el bloqueo del recurso que modifica (el
 *     pedido), retenido brevemente por los handlers de la saga
 */
public record ConfiguracionIdempotencia(
    Duration ttl,
    Duration reintentarDespues,
    Duration esperaBloqueo,
    Duration leaseBloqueo,
    String prefijoBloqueo,
    Duration esperaBloqueoRecurso) {}
