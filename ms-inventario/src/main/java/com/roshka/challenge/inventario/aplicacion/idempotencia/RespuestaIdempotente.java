package com.roshka.challenge.inventario.aplicacion.idempotencia;

public record RespuestaIdempotente(RespuestaAlmacenada respuestaHttp, boolean reproducido) {}
