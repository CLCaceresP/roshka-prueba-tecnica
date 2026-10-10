package com.roshka.challenge.pedidos.aplicacion.consulta;

/**
 * @param tamanioPorDefecto tamaño de página cuando el cliente no lo indica
 * @param tamanioMaximo tamaño de página más grande que se acepta
 */
public record ConfiguracionPaginacion(int tamanioPorDefecto, int tamanioMaximo) {}
