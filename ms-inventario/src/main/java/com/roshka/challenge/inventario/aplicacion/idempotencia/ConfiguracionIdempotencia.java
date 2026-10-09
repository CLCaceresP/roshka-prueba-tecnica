package com.roshka.challenge.inventario.aplicacion.idempotencia;

import java.time.Duration;

/**
 * @param prefijoBloqueo prefijo del nombre del bloqueo distribuido, p. ej. {@code
 *     idem:ms-inventario:}
 * @param esperaBloqueo cuánto espera un duplicado el bloqueo antes de recibir 409; cero responde de
 *     inmediato
 */
public record ConfiguracionIdempotencia(
    Duration ttl,
    Duration reintentarDespues,
    Duration esperaBloqueo,
    Duration leaseBloqueo,
    String prefijoBloqueo) {}
