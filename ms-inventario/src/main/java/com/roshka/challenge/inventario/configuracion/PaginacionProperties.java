package com.roshka.challenge.inventario.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("paginacion")
record PaginacionProperties(int tamanioPorDefecto, int tamanioMaximo) {}
