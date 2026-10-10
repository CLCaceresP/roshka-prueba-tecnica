package com.roshka.challenge.pedidos.aplicacion.saga;

import java.time.Duration;

public record ConfiguracionSaga(
    Duration umbralTimeout,
    int tamanioLoteTimeout,
    Duration esperaBloqueoPedido,
    Duration leaseBloqueoPedido) {}
