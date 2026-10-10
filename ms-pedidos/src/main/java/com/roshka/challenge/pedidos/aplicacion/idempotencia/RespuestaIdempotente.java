package com.roshka.challenge.pedidos.aplicacion.idempotencia;

public record RespuestaIdempotente(RespuestaAlmacenada respuestaHttp, boolean reproducido) {}
