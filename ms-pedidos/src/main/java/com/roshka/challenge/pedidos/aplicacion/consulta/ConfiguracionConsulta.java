package com.roshka.challenge.pedidos.aplicacion.consulta;

import java.time.ZoneId;

/**
 * @param zona zona horaria en la que se interpretan los días de las consultas
 * @param rangoMaximoDias cantidad máxima de días que puede abarcar un rango
 */
public record ConfiguracionConsulta(ZoneId zona, int rangoMaximoDias) {}
