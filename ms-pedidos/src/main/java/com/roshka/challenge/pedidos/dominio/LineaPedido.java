package com.roshka.challenge.pedidos.dominio;

import java.util.UUID;

public record LineaPedido(UUID idProducto, int cantidad) {}
